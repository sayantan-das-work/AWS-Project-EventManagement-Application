package personalproject.eventmgmntapp.hiveup.signup.error;

/**
 * One entry of the error catalogue.
 *
 * @param code       unique error code sent to API clients
 * @param httpStatus HTTP status returned with this error
 * @param message    publicly displayable message (never contains internals)
 */
public record ErrorCode(String code, int httpStatus, String message) {
}
