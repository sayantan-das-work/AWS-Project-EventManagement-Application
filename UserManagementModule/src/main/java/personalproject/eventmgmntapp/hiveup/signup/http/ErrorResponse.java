package personalproject.eventmgmntapp.hiveup.signup.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import personalproject.eventmgmntapp.hiveup.signup.validation.FieldError;

import java.util.List;

/**
 * Error body sent to clients; never contains stack traces or internal details.
 *
 * @param errorCode catalogue error code
 * @param message   public message
 * @param errors    field errors, present only for validation failures
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String errorCode, String message, List<FieldError> errors) {
}
