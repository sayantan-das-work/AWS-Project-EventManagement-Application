package personalproject.eventmgmntapp.hiveup.signup.http;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.muserver.MuServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import personalproject.eventmgmntapp.hiveup.signup.repository.CognitoUserRepository;
import personalproject.eventmgmntapp.hiveup.signup.repository.UserProfileRepository;
import personalproject.eventmgmntapp.hiveup.signup.service.SignupService;
import personalproject.eventmgmntapp.hiveup.signup.util.RetryExecutor;
import personalproject.eventmgmntapp.hiveup.signup.validation.RuleLoader;
import personalproject.eventmgmntapp.hiveup.signup.validation.SignupValidator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/** End-to-end (HTTP + resource + service) check that the password never reaches the logs or responses. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordLoggingTest {
    private static final String PASSWORD = "Zq9#SecretPw";
    private static final String BODY = "{\"first_name\":\"Asha\",\"last_name\":\"Rao\",\"date_of_birth\":\"1995-04-12\","
            + "\"email\":\"asha@example.com\",\"password\":\"" + PASSWORD + "\",\"contact_number\":\"+919876543210\"}";

    @Mock
    private CognitoUserRepository cognito;
    @Mock
    private UserProfileRepository profiles;

    private final HttpClient client = HttpClient.newHttpClient();
    private ListAppender<ILoggingEvent> appender;
    private Logger root;
    private MuServer server;

    @BeforeEach
    void setUp() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);

        Clock clock = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC);
        SignupService service = new SignupService(new SignupValidator(RuleLoader.loadDefault(), clock), cognito,
                profiles, new RetryExecutor(new long[]{0, 0}, ms -> { }), clock);
        server = HttpServerBootstrap.start(0, service);
        when(cognito.createUser(anyString())).thenReturn("uid-1");
    }

    @AfterEach
    void tearDown() {
        server.stop();
        root.detachAppender(appender);
    }

    private HttpResponse<String> send(String body) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + server.uri().getPort() + "/users/signup"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertFalse(response.body().contains(PASSWORD), "password leaked in response");
        return response;
    }

    private void assertNoPasswordInLogs() {
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(PASSWORD), event.getFormattedMessage());
            assertTrue(event.getThrowableProxy() == null
                    || !String.valueOf(event.getThrowableProxy().getMessage()).contains(PASSWORD));
        }
    }

    @Test
    void successPathDoesNotLogPassword() throws Exception {
        assertEquals(201, send(BODY).statusCode());
        assertFalse(appender.list.isEmpty());
        assertNoPasswordInLogs();
    }

    @Test
    void rollbackPathDoesNotLogPassword() throws Exception {
        doThrow(new SignupException(ErrorCodes.DB_FAILURE, false, new RuntimeException(PASSWORD)))
                .when(profiles).save(any());
        assertEquals(500, send(BODY).statusCode());
        assertNoPasswordInLogs();
    }

    @Test
    void rejectedPasswordPathDoesNotLogPassword() throws Exception {
        doThrow(new SignupException(ErrorCodes.PASSWORD_REJECTED)).when(cognito).setPermanentPassword(anyString(), anyString());
        assertEquals(400, send(BODY).statusCode());
        assertNoPasswordInLogs();
    }

    @Test
    void unexpectedErrorCarryingPasswordTextDoesNotLogIt() throws Exception {
        doThrow(new IllegalStateException("failure with " + PASSWORD)).when(profiles).save(any());
        assertEquals(500, send(BODY).statusCode());
        assertNoPasswordInLogs();
    }

    @Test
    void failedRollbackDoesNotLogPassword() throws Exception {
        doThrow(new IllegalStateException("x " + PASSWORD)).when(profiles).save(any());
        doThrow(new RuntimeException("rollback " + PASSWORD)).when(cognito).deleteUser(anyString());
        assertEquals(500, send(BODY).statusCode());
        assertNoPasswordInLogs();
    }

    @Test
    void malformedBodyContainingPasswordDoesNotLogIt() throws Exception {
        assertEquals(400, send("{\"password\":\"" + PASSWORD + "\" oops").statusCode());
        assertNoPasswordInLogs();
    }
}
