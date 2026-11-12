package personalproject.eventmgmntapp.hiveup.signup.http;

import io.muserver.MuServer;
import io.muserver.MuServerBuilder;
import io.muserver.rest.RestHandlerBuilder;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCatalog;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCode;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.service.SignupService;

/** Starts the muServer HTTP server with the annotated JAX-RS resources and JSON error handling. */
public final class HttpServerBootstrap {
    /** Maximum request body size in bytes (16 KB). */
    public static final long MAX_REQUEST_BYTES = 16 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerBootstrap.class);

    private HttpServerBootstrap() {
    }

    /**
     * Starts the server and registers a shutdown hook that stops it.
     *
     * @param port    HTTP port (0 picks a free port)
     * @param service signup service used by {@link SignupResource}
     * @return the running server
     */
    public static MuServer start(int port, SignupService service) {
        JsonMessageBodyProvider json = new JsonMessageBodyProvider();
        MuServer server = MuServerBuilder.httpServer()
                .withHttpPort(port)
                .withMaxRequestSize(MAX_REQUEST_BYTES)
                // One entry handler wraps everything: correlation id, START/END lines, MDC cleanup.
                .addHandler(new RequestContextHandler(
                        RestHandlerBuilder.restHandler(new SignupResource(service))
                                .addCustomReader(json)
                                .addCustomWriter(json)
                                .addExceptionMapper(personalproject.eventmgmntapp.hiveup.signup.error.SignupException.class,
                                        new ErrorMappers.SignupExceptionMapper())
                                .addExceptionMapper(WebApplicationException.class,
                                        new ErrorMappers.WebApplicationExceptionMapper())
                                .addExceptionMapper(Throwable.class, new ErrorMappers.UnexpectedExceptionMapper())
                                .build(),
                        (request, response) -> {
                            // Reached only when no resource path matched.
                            ErrorCode notFound = ErrorCatalog.get(ErrorCodes.ROUTE_NOT_FOUND);
                            LOG.warn("No route for path={}", request.relativePath());
                            response.status(notFound.httpStatus());
                            response.contentType(MediaType.APPLICATION_JSON);
                            response.write(JsonSupport.toJson(
                                    new ErrorResponse(notFound.code(), notFound.message(), null)));
                            return true;
                        }))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "server-shutdown"));
        LOG.info("User management server started on port {}", server.uri().getPort());
        return server;
    }
}
