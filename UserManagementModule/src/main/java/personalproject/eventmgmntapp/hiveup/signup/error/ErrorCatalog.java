package personalproject.eventmgmntapp.hiveup.signup.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads {@code errors.json} once and resolves error codes to their definitions. */
public final class ErrorCatalog {
    private static final String RESOURCE = "/errors.json";
    private static final Map<String, ErrorCode> CODES = load();

    private ErrorCatalog() {
    }

    /**
     * Looks up an error definition.
     *
     * @param code error code, e.g. {@code SIGNUP_EMAIL_ALREADY_EXISTS}
     * @return the matching definition
     * @throws IllegalArgumentException if the code is not in the catalogue
     */
    public static ErrorCode get(String code) {
        ErrorCode found = CODES.get(code);
        if (found == null) {
            throw new IllegalArgumentException("Unknown error code: " + code);
        }
        return found;
    }

    /** @return unmodifiable view of all catalogue entries keyed by code */
    public static Map<String, ErrorCode> all() {
        return CODES;
    }

    private static Map<String, ErrorCode> load() {
        try (InputStream in = ErrorCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + RESOURCE);
            }
            JsonNode root = new ObjectMapper().readTree(in);
            Map<String, ErrorCode> map = new LinkedHashMap<>();
            for (JsonNode node : root.get("errors")) {
                ErrorCode entry = new ErrorCode(node.get("code").asText(),
                        node.get("httpStatus").asInt(), node.get("message").asText());
                if (map.put(entry.code(), entry) != null) {
                    throw new IllegalStateException("Duplicate error code: " + entry.code());
                }
            }
            return Collections.unmodifiableMap(map);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }
}
