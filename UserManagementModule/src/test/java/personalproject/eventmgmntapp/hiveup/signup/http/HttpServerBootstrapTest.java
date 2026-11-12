package personalproject.eventmgmntapp.hiveup.signup.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.muserver.MuServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import personalproject.eventmgmntapp.hiveup.signup.error.ValidationException;
import personalproject.eventmgmntapp.hiveup.signup.model.SignupRequest;
import personalproject.eventmgmntapp.hiveup.signup.model.SignupResponse;
import personalproject.eventmgmntapp.hiveup.signup.service.SignupService;
import personalproject.eventmgmntapp.hiveup.signup.validation.FieldError;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Starts the real muServer with the annotated resource on a free port and calls it over HTTP. */
@ExtendWith(MockitoExtension.class)
class HttpServerBootstrapTest {
    private static final String JSON = "application/json";
    private static final String VALID_BODY = """
            {"first_name":"Asha","last_name":"Rao","gender":"female","date_of_birth":"1995-04-12",
             "email":"asha@example.com","password":"Str0ng#Pass","profession":"Engineer",
             "contact_number":"+919876543210","favourite_colour":"blue"}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Mock
    private SignupService service;
    private MuServer server;

    @BeforeEach
    void setUp() {
        server = HttpServerBootstrap.start(0, service);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> send(String method, String path, String contentType, String accept, String body)
            throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + server.uri().getPort() + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        if (accept != null) {
            b.header("Accept", accept);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String contentType, String body) throws Exception {
        return send("POST", "/users/signup", contentType, null, body);
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith(JSON), "error must be JSON");
        JsonNode json = mapper.readTree(response.body());
        assertEquals(code, json.get("errorCode").asText());
        assertFalse(json.get("message").asText().isBlank());
    }

    @Test
    void validRequestReturns201WithJsonBodyAndIgnoresUnknownFields() throws Exception {
        when(service.signup(any(SignupRequest.class))).thenReturn(new SignupResponse("uid-9", "ok"));

        HttpResponse<String> response = post(JSON, VALID_BODY);

        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith(JSON));
        JsonNode json = mapper.readTree(response.body());
        assertEquals("uid-9", json.get("userId").asText());
        assertEquals("ok", json.get("message").asText());

        ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
        verify(service).signup(captor.capture());
        SignupRequest r = captor.getValue();
        assertEquals("Asha", r.firstName());
        assertEquals("Rao", r.lastName());
        assertEquals("female", r.gender());
        assertEquals("1995-04-12", r.dateOfBirth());
        assertEquals("asha@example.com", r.email());
        assertEquals("Str0ng#Pass", r.password());
        assertEquals("Engineer", r.profession());
        assertEquals("+919876543210", r.contactNumber());
    }

    @Test
    void everyResponseCarriesAGeneratedCorrelationId() throws Exception {
        when(service.signup(any(SignupRequest.class))).thenReturn(new SignupResponse("u", "m"));

        String ok = post(JSON, VALID_BODY).headers().firstValue(RequestContextHandler.HEADER).orElse("");
        String notFound = send("GET", "/nope", null, null, null).headers()
                .firstValue(RequestContextHandler.HEADER).orElse("");

        assertEquals(36, ok.length(), "a UUID is generated");
        assertEquals(36, notFound.length(), "also for the fallback 404 handler");
        assertFalse(ok.equals(notFound), "each request gets its own id");
    }

    @Test
    void callerSuppliedCorrelationIdIsEchoedAndVisibleInServiceLogContext() throws Exception {
        String[] seenInService = new String[1];
        when(service.signup(any(SignupRequest.class))).thenAnswer(inv -> {
            seenInService[0] = org.slf4j.MDC.get(RequestContextHandler.MDC_KEY);
            return new SignupResponse("u", "m");
        });
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.uri().getPort() + "/users/signup"))
                .header("Content-Type", JSON).header(RequestContextHandler.HEADER, "trace-123")
                .POST(HttpRequest.BodyPublishers.ofString(VALID_BODY)).build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals("trace-123", response.headers().firstValue(RequestContextHandler.HEADER).orElse(""));
        assertEquals("trace-123", seenInService[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = {"has space", "bad\tchar", "semi;colon", "{json}"})
    void unsafeCallerIdsAreReplaced(String supplied) {
        String id = RequestContextHandler.resolveId(supplied);
        assertEquals(36, id.length());
        assertFalse(id.equals(supplied));
    }

    @Test
    void tooLongOrMissingCallerIdsAreReplaced() {
        assertEquals(36, RequestContextHandler.resolveId("a".repeat(65)).length());
        assertEquals(36, RequestContextHandler.resolveId(null).length());
        assertEquals("a".repeat(64), RequestContextHandler.resolveId("a".repeat(64)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json; charset=utf-8", "Application/JSON", "application/json;charset=UTF-8"})
    void jsonContentTypeVariantsAreAccepted(String contentType) throws Exception {
        when(service.signup(any(SignupRequest.class))).thenReturn(new SignupResponse("u", "m"));
        assertEquals(201, post(contentType, VALID_BODY).statusCode());
    }

    @Test
    void unknownPathReturnsJson404() throws Exception {
        assertError(send("POST", "/nope", JSON, null, VALID_BODY), 404, ErrorCodes.ROUTE_NOT_FOUND);
        assertError(send("GET", "/", null, null, null), 404, ErrorCodes.ROUTE_NOT_FOUND);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE", "PATCH"})
    void otherMethodsOnSignupPathReturnJson405(String method) throws Exception {
        HttpResponse<String> response = send(method, "/users/signup", JSON, null, null);
        assertError(response, 405, ErrorCodes.METHOD_NOT_ALLOWED);
        assertTrue(response.headers().firstValue("Allow").orElse("").contains("POST"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/xml", "multipart/form-data", "application/x-www-form-urlencoded"})
    void wrongContentTypeReturns415(String contentType) throws Exception {
        assertError(post(contentType, VALID_BODY), 415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE);
        verifyNoInteractions(service);
    }

    @Test
    void missingContentTypeReturns415() throws Exception {
        assertError(post(null, VALID_BODY), 415, ErrorCodes.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void acceptHeaderThatExcludesJsonReturns406() throws Exception {
        assertError(send("POST", "/users/signup", JSON, "text/plain", VALID_BODY), 406, ErrorCodes.REQUEST_NOT_ACCEPTABLE);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{bad json", "", "   ", "null", "[]", "\"text\"", "{\"first_name\":\"a\"} trailing",
            "{\"first_name\":{\"x\":1}}"})
    void malformedBodiesReturn400(String body) throws Exception {
        assertError(post(JSON, body), 400, ErrorCodes.MALFORMED_JSON);
        verifyNoInteractions(service);
    }

    @Test
    void validationFailureReturns400WithAllFieldErrors() throws Exception {
        when(service.signup(any(SignupRequest.class))).thenThrow(new ValidationException(List.of(
                new FieldError("email", "SIGNUP_EMAIL_INVALID", "bad email"),
                new FieldError("contact_number", "SIGNUP_CONTACT_INVALID", "bad phone"))));

        HttpResponse<String> response = post(JSON, VALID_BODY);

        assertError(response, 400, ErrorCodes.VALIDATION_FAILED);
        JsonNode errors = mapper.readTree(response.body()).get("errors");
        assertEquals(2, errors.size());
        assertEquals("email", errors.get(0).get("field").asText());
        assertEquals("SIGNUP_CONTACT_INVALID", errors.get(1).get("code").asText());
        assertEquals("bad phone", errors.get(1).get("message").asText());
    }

    @ParameterizedTest
    @CsvSource({
            "SIGNUP_EMAIL_ALREADY_EXISTS,409",
            "SIGNUP_PASSWORD_REJECTED,400",
            "SIGNUP_THROTTLED,429",
            "SIGNUP_COGNITO_FAILURE,500",
            "SIGNUP_DB_FAILURE,500",
            "SIGNUP_INTERNAL_ERROR,500"})
    void signupExceptionsMapToCatalogueStatusAndCode(String code, int status) throws Exception {
        when(service.signup(any(SignupRequest.class))).thenThrow(new SignupException(code));

        HttpResponse<String> response = post(JSON, VALID_BODY);

        assertError(response, status, code);
        assertFalse(mapper.readTree(response.body()).has("errors"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingDetails() throws Exception {
        when(service.signup(any(SignupRequest.class))).thenThrow(new IllegalStateException("secret internal detail"));

        HttpResponse<String> response = post(JSON, VALID_BODY);

        assertError(response, 500, ErrorCodes.INTERNAL_ERROR);
        assertFalse(response.body().contains("secret internal detail"));
        assertFalse(response.body().contains("IllegalStateException"));
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
        String huge = "{\"first_name\":\"" + "a".repeat(40_000) + "\"}";
        assertEquals(413, post(JSON, huge).statusCode());
        verifyNoInteractions(service);
    }

    @Test
    void passwordNeverAppearsInAnyResponse() throws Exception {
        when(service.signup(any(SignupRequest.class)))
                .thenReturn(new SignupResponse("u", "m"))
                .thenThrow(new SignupException(ErrorCodes.PASSWORD_REJECTED))
                .thenThrow(new RuntimeException("Str0ng#Pass"));

        for (int i = 0; i < 3; i++) {
            assertFalse(post(JSON, VALID_BODY).body().contains("Str0ng#Pass"));
        }
        assertFalse(post(JSON, "{\"password\":\"Str0ng#Pass\" broken").body().contains("Str0ng#Pass"));
    }
}
