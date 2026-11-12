package personalproject.eventmgmntapp.hiveup.signup.repository;

/** Cognito operations needed for signup. Implementations map SDK errors to SignupException. */
public interface CognitoUserRepository {
    /**
     * Creates a user with email as username, {@code email_verified=false} and no Cognito message.
     *
     * @param email user email (also the username)
     * @return the new user's {@code sub} (userId)
     * @throws personalproject.eventmgmntapp.hiveup.signup.error.SignupException EMAIL_ALREADY_EXISTS,
     *         THROTTLED or COGNITO_FAILURE
     */
    String createUser(String email);

    /**
     * Sets a permanent password so the user can sign in.
     *
     * @param email    username
     * @param password password as supplied by the user
     * @throws personalproject.eventmgmntapp.hiveup.signup.error.SignupException PASSWORD_REJECTED,
     *         THROTTLED or COGNITO_FAILURE
     */
    void setPermanentPassword(String email, String password);

    /**
     * Adds the user to a group.
     *
     * @param email     username
     * @param groupName group name
     * @throws personalproject.eventmgmntapp.hiveup.signup.error.SignupException THROTTLED or COGNITO_FAILURE
     */
    void addToGroup(String email, String groupName);

    /**
     * Deletes the user (used for rollback).
     *
     * @param email username
     * @throws personalproject.eventmgmntapp.hiveup.signup.error.SignupException THROTTLED or COGNITO_FAILURE
     */
    void deleteUser(String email);
}
