package personalproject.eventmgmntapp.hiveup.signup.http;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Shared Jackson mapper: ignores unknown fields, rejects trailing tokens, never echoes input in errors. */
public final class JsonSupport {
    private static final ObjectMapper MAPPER = JsonMapper.builder(
                    JsonFactory.builder().disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private JsonSupport() {
    }

    /**
     * Parses JSON into a type.
     *
     * @param json JSON text
     * @param type target type
     * @param <T>  target type
     * @return parsed value (may be null for the JSON literal {@code null})
     * @throws JsonProcessingException when the JSON is malformed or does not fit the type
     */
    public static <T> T parse(String json, Class<T> type) throws JsonProcessingException {
        return MAPPER.readValue(json, type);
    }

    /**
     * Serializes a value to JSON.
     *
     * @param value value to serialize
     * @return JSON text
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize response", e);
        }
    }
}
