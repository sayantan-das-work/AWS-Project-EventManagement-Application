package personalproject.eventmgmntapp.hiveup.signup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import personalproject.eventmgmntapp.hiveup.signup.error.ValidationException;
import personalproject.eventmgmntapp.hiveup.signup.model.SignupRequest;
import personalproject.eventmgmntapp.hiveup.signup.model.SignupResponse;
import personalproject.eventmgmntapp.hiveup.signup.model.UserProfile;
import personalproject.eventmgmntapp.hiveup.signup.model.UserStatus;
import personalproject.eventmgmntapp.hiveup.signup.repository.CognitoUserRepository;
import personalproject.eventmgmntapp.hiveup.signup.repository.UserProfileRepository;
import personalproject.eventmgmntapp.hiveup.signup.util.EmailRedactor;
import personalproject.eventmgmntapp.hiveup.signup.util.RetryExecutor;
import personalproject.eventmgmntapp.hiveup.signup.validation.FieldError;
import personalproject.eventmgmntapp.hiveup.signup.validation.SignupValidator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/** Orchestrates signup: validate, create Cognito user, store profile, roll back on failure. */
public class SignupService {
    /** Cognito group every new user joins. */
    public static final String DEFAULT_GROUP = "public";
    /** Time an unverified profile is kept before it may be removed. */
    public static final Duration VERIFICATION_WINDOW = Duration.ofHours(24);
    /** Message returned on success. */
    public static final String SUCCESS_MESSAGE = "User registered. Please verify your email.";

    private static final Logger LOG = LoggerFactory.getLogger(SignupService.class);

    private final SignupValidator validator;
    private final CognitoUserRepository cognito;
    private final UserProfileRepository profiles;
    private final RetryExecutor retry;
    private final Clock clock;

    /**
     * @param validator rule-driven request validator
     * @param cognito   Cognito access
     * @param profiles  profile storage
     * @param retry     retry policy for the profile write
     * @param clock     time source
     */
    public SignupService(SignupValidator validator, CognitoUserRepository cognito, UserProfileRepository profiles,
                         RetryExecutor retry, Clock clock) {
        this.validator = validator;
        this.cognito = cognito;
        this.profiles = profiles;
        this.retry = retry;
        this.clock = clock;
    }

    /**
     * Registers a new user.
     *
     * @param rawRequest parsed request body
     * @return userId and next-step message
     * @throws ValidationException when any field is invalid
     * @throws SignupException     EMAIL_ALREADY_EXISTS, PASSWORD_REJECTED, THROTTLED, COGNITO_FAILURE,
     *                             DB_FAILURE or INTERNAL_ERROR; the Cognito user is rolled back when it was created
     */
    public SignupResponse signup(SignupRequest rawRequest) {
        SignupRequest request = rawRequest.withTrimmedEmail();
        LOG.info("Signup request received: {}", request);

        LOG.info("Validating signup fields");
        List<FieldError> errors = validator.validate(request.toFieldMap());
        if (!errors.isEmpty()) {
            LOG.error("Signup validation failed: {}", errors);
            throw new ValidationException(errors);
        }
        LOG.info("Completed validating signup fields");

        String email = request.email();

        LOG.info("Step 1/4 Creating Cognito user for email={}", EmailRedactor.redact(email));
        String userId = cognito.createUser(email);
        LOG.info("Step 1/4 done: Cognito user created userId={}", userId);
        String step = "2/4 set permanent password";
        try {
            LOG.info("Step 2/4 Setting permanent password userId={}", userId);
            cognito.setPermanentPassword(email, request.password());
            step = "3/4 add to group";
            LOG.info("Step 3/4 Adding userId={} to group={}", userId, DEFAULT_GROUP);
            cognito.addToGroup(email, DEFAULT_GROUP);
            step = "4/4 save profile";
            LOG.info("Step 4/4 Saving profile to DynamoDB userId={}", userId);
            UserProfile profile = buildProfile(userId, request);
            retry.execute(() -> {
                profiles.save(profile);
                return null;
            }, e -> e instanceof SignupException se && se.isRetryable());
            LOG.info("Step 4/4 done: profile saved userId={}", userId);
        } catch (RuntimeException e) {
            String failure = e instanceof SignupException se ? se.getCode() : e.getClass().getSimpleName();
            LOG.error("Signup FAILED at step {} userId={} reason={}; rolling back", step, userId, failure);
            rollback(email, userId);
            if (e instanceof SignupException) {
                throw e;
            }
            throw new SignupException(ErrorCodes.INTERNAL_ERROR, false, e);
        } catch (Error e) {
            LOG.error("Signup FAILED at step {} userId={} reason={}; rolling back", step, userId,
                    e.getClass().getSimpleName());
            // e.g. NoClassDefFoundError or OutOfMemoryError: the user must not be left behind either
            rollback(email, userId);
            throw e;
        }
        LOG.info("Signup completed for userId={}", userId);
        return new SignupResponse(userId, SUCCESS_MESSAGE);
    }

    private UserProfile buildProfile(String userId, SignupRequest r) {
        Instant now = clock.instant();
        return new UserProfile(userId, r.firstName(), r.lastName(), normalizeGender(r.gender()), r.dateOfBirth(),
                r.email(), r.profession(), r.contactNumber(), now, UserStatus.UNVERIFIED,
                now.plus(VERIFICATION_WINDOW).getEpochSecond());
    }

    /** Capitalizes a validated gender, e.g. {@code male} to {@code Male}; null stays null. */
    static String normalizeGender(String gender) {
        if (gender == null) {
            return null;
        }
        String g = gender.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(g.charAt(0)) + g.substring(1);
    }

    /** Deletes the Cognito user; a failure here is logged for manual cleanup and never masks the original error. */
    private void rollback(String email, String userId) {
        LOG.warn("Rollback: deleting Cognito user userId={}", userId);
        try {
            cognito.deleteUser(email);
            LOG.warn("Rollback done: Cognito user deleted userId={}", userId);
        } catch (RuntimeException rollbackFailure) {
            String reason = rollbackFailure instanceof SignupException se ? se.getCode()
                    : rollbackFailure.getClass().getSimpleName();
            LOG.error("ROLLBACK FAILED, manual cleanup needed for userId={} reason={} (see the preceding "
                    + "'Cognito AdminDeleteUser failed' line for the AWS error code)", userId, reason);
        }
    }
}
