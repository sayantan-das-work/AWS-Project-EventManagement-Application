package personalproject.eventmgmntapp.hiveup.signup.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

/** {@link CognitoUserRepository} backed by the Cognito Admin APIs. */
public class AwsCognitoUserRepository implements CognitoUserRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AwsCognitoUserRepository.class);

    private final CognitoIdentityProviderClient client;
    private final String userPoolId;

    /**
     * @param client     Cognito client
     * @param userPoolId user pool id
     */
    public AwsCognitoUserRepository(CognitoIdentityProviderClient client, String userPoolId) {
        this.client = client;
        this.userPoolId = userPoolId;
    }

    @Override
    public String createUser(String email) {
        try {
            AdminCreateUserResponse response = client.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .userAttributes(attribute("email", email), attribute("email_verified", "false"))
                    .messageAction(MessageActionType.SUPPRESS)
                    .build());
            return response.user().attributes().stream()
                    .filter(a -> "sub".equals(a.name())).map(AttributeType::value).findFirst()
                    .orElseThrow(() -> new SignupException(ErrorCodes.COGNITO_FAILURE));
        } catch (UsernameExistsException e) {
            throw new SignupException(ErrorCodes.EMAIL_ALREADY_EXISTS);
        } catch (SdkException e) {
            throw mapFailure("AdminCreateUser", e);
        }
    }

    @Override
    public void setPermanentPassword(String email, String password) {
        try {
            client.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId).username(email).password(password).permanent(true).build());
        } catch (InvalidPasswordException e) {
            throw new SignupException(ErrorCodes.PASSWORD_REJECTED);
        } catch (SdkException e) {
            throw mapFailure("AdminSetUserPassword", e);
        }
    }

    @Override
    public void addToGroup(String email, String groupName) {
        try {
            client.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId).username(email).groupName(groupName).build());
        } catch (SdkException e) {
            throw mapFailure("AdminAddUserToGroup", e);
        }
    }

    @Override
    public void deleteUser(String email) {
        try {
            client.adminDeleteUser(AdminDeleteUserRequest.builder().userPoolId(userPoolId).username(email).build());
        } catch (SdkException e) {
            throw mapFailure("AdminDeleteUser", e);
        }
    }

    private static AttributeType attribute(String name, String value) {
        return AttributeType.builder().name(name).value(value).build();
    }

    /** Maps an SDK failure to THROTTLED or COGNITO_FAILURE; logs only the operation and AWS error code. */
    private static SignupException mapFailure(String operation, SdkException e) {
        String awsCode = e instanceof AwsServiceException ase && ase.awsErrorDetails() != null
                ? ase.awsErrorDetails().errorCode()
                : e.getClass().getSimpleName();

        LOG.error("Cognito {} failed: {}", operation, awsCode);

        boolean throttled =
                e instanceof TooManyRequestsException
                        || e instanceof LimitExceededException
                        || (e instanceof AwsServiceException ase && ase.isThrottlingException());
        return new SignupException(throttled ? ErrorCodes.THROTTLED : ErrorCodes.COGNITO_FAILURE, false, e);
    }
}
