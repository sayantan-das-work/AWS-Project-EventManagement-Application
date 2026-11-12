package personalproject.eventmgmntapp.hiveup.signup.http;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.config.SsmConfigLoader;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import personalproject.eventmgmntapp.hiveup.signup.model.SignupRequest;
import personalproject.eventmgmntapp.hiveup.signup.service.SignupService;

/** JAX-RS resource for user signup. Errors are translated by the exception mappers in {@link ErrorMappers}. */
@Path("/users")
public class SignupResource {
    private final SignupService service;

    /** @param service signup service */
    public SignupResource(SignupService service) {
        this.service = service;
    }

    /**
     * Registers a new user.
     *
     * @param request parsed JSON body
     * @return 201 with {@code {userId, message}}
     * @throws SignupException MALFORMED_JSON when the body is the JSON literal null; otherwise any
     *                         failure of {@link SignupService#signup(SignupRequest)}
     */
    @POST
    @Path("/signup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response signup(SignupRequest request) {
        if (request == null) {
            throw new SignupException(ErrorCodes.MALFORMED_JSON);
        }
        return Response.status(Response.Status.CREATED).entity(service.signup(request)).build();
    }
}
