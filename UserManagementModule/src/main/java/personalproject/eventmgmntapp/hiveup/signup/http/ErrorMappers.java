package personalproject.eventmgmntapp.hiveup.signup.http;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCatalog;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCode;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import personalproject.eventmgmntapp.hiveup.signup.error.ValidationException;
import personalproject.eventmgmntapp.hiveup.signup.validation.FieldError;

import java.util.List;

/** JAX-RS exception mappers that turn every failure into the JSON error body from the error catalogue. */
public final class ErrorMappers {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorMappers.class);

    private ErrorMappers() {
    }

    /**
     * Builds a JSON error response for a catalogue code.
     *
     * @param code        catalogue error code
     * @param fieldErrors validation errors to include, or null
     * @return response with the catalogue's HTTP status and an {@link ErrorResponse} body
     */
    static Response toResponse(String code, List<FieldError> fieldErrors) {
        ErrorCode def = ErrorCatalog.get(code);
        return Response.status(def.httpStatus()).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(def.code(), def.message(), fieldErrors)).build();
    }

    /** Maps {@link SignupException} (and {@link ValidationException}) to their catalogue status and body. */
    public static class SignupExceptionMapper implements ExceptionMapper<SignupException> {
        @Override
        public Response toResponse(SignupException e) {
            LOG.warn("Request failed: errorCode={}", e.getCode());
            return ErrorMappers.toResponse(e.getCode(), e instanceof ValidationException v ? v.getErrors() : null);
        }
    }

    /** Maps framework errors (404, 405, 406, 415, other 4xx) to catalogue codes; keeps the Allow header on 405. */
    public static class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
        @Override
        public Response toResponse(WebApplicationException e) {
            int status = e.getResponse().getStatus();
            String code = switch (status) {
                case 404 -> ErrorCodes.ROUTE_NOT_FOUND;
                case 405 -> ErrorCodes.METHOD_NOT_ALLOWED;
                case 406 -> ErrorCodes.REQUEST_NOT_ACCEPTABLE;
                case 415 -> ErrorCodes.UNSUPPORTED_MEDIA_TYPE;
                default -> status >= 500 ? ErrorCodes.INTERNAL_ERROR : ErrorCodes.BAD_REQUEST;
            };
            LOG.warn("Request rejected by framework: httpStatus={} errorCode={}", status, code);
            Response response = ErrorMappers.toResponse(code, null);
            String allow = e.getResponse().getHeaderString("Allow");
            if (status == 405 && allow != null) {
                return Response.fromResponse(response).header("Allow", allow).build();
            }
            return response;
        }
    }

    /** Catch-all: logs only the exception class (messages may hold request data) and returns a generic 500. */
    public static class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {
        @Override
        public Response toResponse(Throwable t) {
            LOG.error("Unexpected error handling request: {}", t.getClass().getName());
            return ErrorMappers.toResponse(ErrorCodes.INTERNAL_ERROR, null);
        }
    }
}
