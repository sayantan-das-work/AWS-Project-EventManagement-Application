package personalproject.eventmgmntapp.hiveup.signup.util;

/** Masks the local part of an email address while retaining its domain for diagnostics. */
public final class EmailRedactor {
    private EmailRedactor() {
    }

    /**
     * Keeps the first character of a valid email's local part and masks the rest.
     * Malformed addresses are fully masked to avoid leaking their contents.
     *
     * @param email email address
     * @return redacted address, or {@code null} when the input is null
     */
    public static String redact(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@') || at == email.length() - 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
