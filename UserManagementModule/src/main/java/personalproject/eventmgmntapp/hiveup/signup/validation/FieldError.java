package personalproject.eventmgmntapp.hiveup.signup.validation;

/**
 * A single field-level validation failure.
 *
 * @param field   request field name, e.g. {@code contact_number}
 * @param code    error code from the catalogue
 * @param message publicly displayable message
 */
public record FieldError(String field, String code, String message) {
}
