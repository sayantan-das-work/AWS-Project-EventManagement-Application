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

        String userId = cognito.createUser(email);
        LOG.info("Created Cognito user with userId={} for email={}", userId, EmailRedactor.redact(email));
        try {
            cognito.setPermanentPassword(email, request.password());
            cognito.addToGroup(email, DEFAULT_GROUP);
            UserProfile profile = buildProfile(userId, request);
            retry.execute(() -> {
                profiles.save(profile);
                return null;
            }, e -> e instanceof SignupException se && se.isRetryable());
        } catch (RuntimeException e) {
            rollback(email, userId);
            if (e instanceof SignupException) {
                throw e;
            }
            LOG.error("Unexpected signup failure for userId={}: {}", userId, e.getClass().getSimpleName());
            throw new SignupException(ErrorCodes.INTERNAL_ERROR, false, e);
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
        try {
            cognito.deleteUser(email);
            LOG.warn("Rolled back Cognito user userId={}", userId);
        } catch (RuntimeException rollbackFailure) {
            LOG.error("ROLLBACK FAILED, manual cleanup needed for userId={}: {}", userId,
                    rollbackFailure.getClass().getSimpleName());
        }
    }
}
