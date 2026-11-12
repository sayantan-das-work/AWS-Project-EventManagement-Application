package personalproject.eventmgmntapp.hiveup.signup.model;

import java.time.Instant;

/**
 * Profile stored in DynamoDB (password is never part of it).
 *
 * @param userId          Cognito {@code sub} (partition key)
 * @param firstName       first name
 * @param lastName        last name
 * @param gender          normalized gender, or null when not supplied
 * @param dateOfBirth     date of birth, YYYY-MM-DD
 * @param email           email as received (trimmed)
 * @param profession      profession, or null when not supplied
 * @param contactNumber   contact number, +91XXXXXXXXXX
 * @param createdAt       creation time (also used as updated_at at signup)
 * @param status          verification status
 * @param expiresAtEpochSeconds deadline after which an unverified profile may be removed
 */
public record UserProfile(
        String userId,
        String firstName,
        String lastName,
        String gender,
        String dateOfBirth,
        String email,
        String profession,
        String contactNumber,
        Instant createdAt,
        UserStatus status,
        long expiresAtEpochSeconds) {
}
