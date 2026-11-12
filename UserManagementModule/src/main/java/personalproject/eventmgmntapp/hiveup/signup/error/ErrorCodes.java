package personalproject.eventmgmntapp.hiveup.signup.error;

/** Error code constants. Every value must exist in {@code errors.json}. */
public final class ErrorCodes {
    public static final String MALFORMED_JSON = "SIGNUP_MALFORMED_JSON";
    public static final String UNSUPPORTED_MEDIA_TYPE = "SIGNUP_UNSUPPORTED_MEDIA_TYPE";
    public static final String VALIDATION_FAILED = "SIGNUP_VALIDATION_FAILED";
    public static final String REQUIRED_FIELD_MISSING = "SIGNUP_REQUIRED_FIELD_MISSING";
    public static final String EMAIL_ALREADY_EXISTS = "SIGNUP_EMAIL_ALREADY_EXISTS";
    public static final String PASSWORD_REJECTED = "SIGNUP_PASSWORD_REJECTED";
    public static final String THROTTLED = "SIGNUP_THROTTLED";
    public static final String COGNITO_FAILURE = "SIGNUP_COGNITO_FAILURE";
    public static final String DB_FAILURE = "SIGNUP_DB_FAILURE";
    public static final String INTERNAL_ERROR = "SIGNUP_INTERNAL_ERROR";
    public static final String CONFIG_LOAD_FAILURE = "CONFIG_LOAD_FAILURE";
    public static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String REQUEST_NOT_ACCEPTABLE = "REQUEST_NOT_ACCEPTABLE";
    public static final String BAD_REQUEST = "BAD_REQUEST";

    private ErrorCodes() {
    }
}
