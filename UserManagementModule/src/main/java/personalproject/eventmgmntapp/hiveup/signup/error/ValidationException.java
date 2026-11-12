package personalproject.eventmgmntapp.hiveup.signup.error;

import personalproject.eventmgmntapp.hiveup.signup.validation.FieldError;

import java.util.List;

/** Request failed field validation; carries every field error. */
public class ValidationException extends SignupException {
    private final transient List<FieldError> errors;

    /** @param errors all field errors found (non-empty) */
    public ValidationException(List<FieldError> errors) {
        super(ErrorCodes.VALIDATION_FAILED);
        this.errors = List.copyOf(errors);
    }

    /** @return immutable list of field errors */
    public List<FieldError> getErrors() {
        return errors;
    }
}
