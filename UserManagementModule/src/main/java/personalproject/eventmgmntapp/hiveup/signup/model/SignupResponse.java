package personalproject.eventmgmntapp.hiveup.signup.model;

/**
 * Successful signup response.
 *
 * @param userId  Cognito {@code sub} of the new user
 * @param message human-readable next step
 */
public record SignupResponse(String userId, String message) {
}
