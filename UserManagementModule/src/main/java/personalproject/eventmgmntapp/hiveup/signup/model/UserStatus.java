package personalproject.eventmgmntapp.hiveup.signup.model;

/** Account verification status stored in DynamoDB. */
public enum UserStatus {
    /** Set at signup; email not yet verified. */
    UNVERIFIED,
    /** Set by the separate verification process. */
    VERIFIED
}
