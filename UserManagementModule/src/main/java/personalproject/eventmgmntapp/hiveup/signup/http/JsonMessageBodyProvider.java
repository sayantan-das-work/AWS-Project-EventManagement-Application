package personalproject.eventmgmntapp.hiveup.signup.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.MessageBodyWriter;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * JSON reader/writer backed by {@link JsonSupport}, so parsing stays strict and never echoes input.
 * A blank or invalid body becomes SIGNUP_MALFORMED_JSON.
 */
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class JsonMessageBodyProvider implements MessageBodyReader<Object>, MessageBodyWriter<Object> {

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return isJson(mediaType);
    }

    @Override
    public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                           MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
            throws IOException, WebApplicationException {
        String body = new String(entityStream.readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            throw new SignupException(ErrorCodes.MALFORMED_JSON);
        }
        try {
            return JsonSupport.parse(body, type);
        } catch (JsonProcessingException e) {
            // Deliberately drop the cause: Jackson messages can quote parts of the input.
            throw new SignupException(ErrorCodes.MALFORMED_JSON);
        }
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return isJson(mediaType);
    }

    @Override
    public void writeTo(Object value, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        entityStream.write(JsonSupport.toJson(value).getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isJson(MediaType mediaType) {
        return mediaType != null && "application".equalsIgnoreCase(mediaType.getType())
                && "json".equalsIgnoreCase(mediaType.getSubtype());
    }
}
