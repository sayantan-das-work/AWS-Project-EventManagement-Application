package personalproject.eventmgmntapp.hiveup.signup.repository;

import personalproject.eventmgmntapp.hiveup.signup.model.UserProfile;

/** Persistence of user profiles. */
public interface UserProfileRepository {
    /**
     * Stores a new profile; fails if the userId already exists.
     *
     * @param profile profile to store
     * @throws personalproject.eventmgmntapp.hiveup.signup.error.SignupException THROTTLED or DB_FAILURE;
     *         {@code isRetryable()} is true for transient failures
     */
    void save(UserProfile profile);
}
