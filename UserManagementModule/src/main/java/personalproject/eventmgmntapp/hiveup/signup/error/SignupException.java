package personalproject.eventmgmntapp.hiveup.signup.error;

/** Business/infrastructure failure mapped to a catalogue error code. */
public class SignupException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    /**
     * @param code error code from {@link ErrorCodes}
     */
    public SignupException(String code) {
        this(code, false, null);
    }

    /**
     * @param code      error code from {@link ErrorCodes}
     * @param retryable whether the failed operation may be retried
     * @param cause     underlying exception (may be null); its message is never sent to clients
     */
    public SignupException(String code, boolean retryable, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.retryable = retryable;
    }

    /** @return the catalogue error code */
    public String getCode() {
        return code;
    }

    /** @return true when the failing operation is safe to retry */
    public boolean isRetryable() {
        return retryable;
    }
}
