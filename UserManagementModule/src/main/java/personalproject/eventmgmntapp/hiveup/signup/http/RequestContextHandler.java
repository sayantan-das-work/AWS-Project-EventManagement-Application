package personalproject.eventmgmntapp.hiveup.signup.http;

import io.muserver.MuHandler;
import io.muserver.MuRequest;
import io.muserver.MuResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Entry point of every HTTP request: assigns a correlation id, puts it in the logging context (MDC key
 * {@value #MDC_KEY}) so that every log line of the request carries it, echoes it in the
 * {@value #HEADER} response header, and logs one START and one END line (status and duration).
 * Wraps the real handlers, trying them in order until one handles the request.
 */
public final class RequestContextHandler implements MuHandler {
    /** MDC key referenced by the logback pattern. */
    public static final String MDC_KEY = "correlationId";
    /** Request and response header carrying the id. */
    public static final String HEADER = "X-Correlation-Id";

    private static final Logger LOG = LoggerFactory.getLogger(RequestContextHandler.class);
    /** A caller supplied id is accepted only when it is short and made of safe characters (no log injection). */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final MuHandler[] delegates;

    /** @param delegates handlers tried in order; the first that returns true wins */
    public RequestContextHandler(MuHandler... delegates) {
        this.delegates = delegates.clone();
    }

    @Override
    public boolean handle(MuRequest request, MuResponse response) throws Exception {
        String correlationId = resolveId(request.headers().get(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.headers().set(HEADER, correlationId);
        long startNanos = System.nanoTime();
        LOG.info("REQUEST START method={} path={}", request.method(), request.relativePath());
        try {
            for (MuHandler delegate : delegates) {
                if (delegate.handle(request, response)) {
                    return true;
                }
            }
            return false;
        } catch (Exception | Error e) {
            LOG.error("REQUEST ABORTED by unhandled {}", e.getClass().getName());
            throw e;
        } finally {
            LOG.info("REQUEST END method={} path={} status={} durationMs={}", request.method(),
                    request.relativePath(), response.status(), (System.nanoTime() - startNanos) / 1_000_000);
            MDC.remove(MDC_KEY);
        }
    }

    /** @return the caller's id when safe, otherwise a new random one */
    static String resolveId(String supplied) {
        if (supplied != null && SAFE_ID.matcher(supplied).matches()) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }
}
