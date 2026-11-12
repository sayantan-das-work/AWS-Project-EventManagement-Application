package personalproject.eventmgmntapp.hiveup.signup.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import personalproject.eventmgmntapp.hiveup.signup.error.ValidationException;
import personalproject.eventmgmntapp.hiveup.signup.model.SignupRequest;
import personalproject.eventmgmntapp.hiveup.signup.model.SignupResponse;
import personalproject.eventmgmntapp.hiveup.signup.model.UserProfile;
import personalproject.eventmgmntapp.hiveup.signup.model.UserStatus;
import personalproject.eventmgmntapp.hiveup.signup.repository.CognitoUserRepository;
import personalproject.eventmgmntapp.hiveup.signup.repository.UserProfileRepository;
import personalproject.eventmgmntapp.hiveup.signup.util.RetryExecutor;
import personalproject.eventmgmntapp.hiveup.signup.validation.RuleLoader;
import personalproject.eventmgmntapp.hiveup.signup.validation.SignupValidator;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final String EMAIL = "Asha@Example.com";

    @Mock
    private CognitoUserRepository cognito;
    @Mock
    private UserProfileRepository profiles;

    private final List<Long> sleeps = new ArrayList<>();
    private SignupService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new SignupService(new SignupValidator(RuleLoader.loadDefault(), clock), cognito, profiles,
                new RetryExecutor(new long[]{100, 200}, sleeps::add), clock);
    }

    private static SignupRequest request() {
        return new SignupRequest("Asha", "Rao", "female", "1995-04-12", "  " + EMAIL + "  ", "Str0ng#Pass",
                "Engineer", "+919876543210");
    }

    @Test
    void happyPathCallsStepsInOrderAndReturnsUserId() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");

        SignupResponse response = service.signup(request());

        assertEquals("uid-1", response.userId());
        assertEquals(SignupService.SUCCESS_MESSAGE, response.message());
        InOrder order = inOrder(cognito, profiles);
        order.verify(cognito).createUser(EMAIL);
        order.verify(cognito).setPermanentPassword(EMAIL, "Str0ng#Pass");
        order.verify(cognito).addToGroup(EMAIL, "public");
        order.verify(profiles).save(any(UserProfile.class));
        verifyNoMoreInteractions(cognito);
    }

    @Test
    void storedProfileIsUnverifiedWithTwentyFourHourExpiryAndNormalizedFields() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");

        service.signup(request());

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profiles).save(captor.capture());
        UserProfile p = captor.getValue();
        assertEquals("uid-1", p.userId());
        assertEquals(UserStatus.UNVERIFIED, p.status());
        assertEquals(NOW, p.createdAt());
        assertEquals(NOW.getEpochSecond() + 24 * 3600, p.expiresAtEpochSeconds());
        assertEquals("Female", p.gender());
        assertEquals(EMAIL, p.email());           // trimmed, case preserved
        assertEquals("+919876543210", p.contactNumber());
        assertEquals("Engineer", p.profession());
    }

    @Test
    void optionalFieldsMayBeAbsent() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        SignupRequest r = new SignupRequest("Asha", "Rao", null, "1995-04-12", EMAIL, "Str0ng#Pass", null, "+919876543210");

        service.signup(r);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(profiles).save(captor.capture());
        assertNull(captor.getValue().gender());
        assertNull(captor.getValue().profession());
    }

    @Test
    void invalidRequestTouchesNoExternalSystem() {
        SignupRequest bad = new SignupRequest("", "Rao", "x", "2999-01-01", "nope", "weak", "", "123");

        ValidationException e = assertThrows(ValidationException.class, () -> service.signup(bad));

        assertEquals(ErrorCodes.VALIDATION_FAILED, e.getCode());
        assertEquals(7, e.getErrors().size());   // every field except last_name
        verifyNoInteractions(cognito, profiles);
    }

    @Test
    void duplicateEmailStopsWithoutRollback() {
        when(cognito.createUser(EMAIL)).thenThrow(new SignupException(ErrorCodes.EMAIL_ALREADY_EXISTS));

        SignupException e = assertThrows(SignupException.class, () -> service.signup(request()));

        assertEquals(ErrorCodes.EMAIL_ALREADY_EXISTS, e.getCode());
        verify(cognito, never()).deleteUser(anyString());
        verify(cognito, never()).setPermanentPassword(anyString(), anyString());
        verifyNoInteractions(profiles);
    }

    @Test
    void rejectedPasswordRollsBackUser() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        doThrow(new SignupException(ErrorCodes.PASSWORD_REJECTED)).when(cognito).setPermanentPassword(EMAIL, "Str0ng#Pass");

        SignupException e = assertThrows(SignupException.class, () -> service.signup(request()));

        assertEquals(ErrorCodes.PASSWORD_REJECTED, e.getCode());
        verify(cognito).deleteUser(EMAIL);
        verify(cognito, never()).addToGroup(anyString(), anyString());
        verifyNoInteractions(profiles);
    }

    @Test
    void groupFailureRollsBackUser() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        doThrow(new SignupException(ErrorCodes.COGNITO_FAILURE)).when(cognito).addToGroup(EMAIL, "public");

        SignupException e = assertThrows(SignupException.class, () -> service.signup(request()));

        assertEquals(ErrorCodes.COGNITO_FAILURE, e.getCode());
        verify(cognito).deleteUser(EMAIL);
        verifyNoInteractions(profiles);
    }

    @Test
    void transientDbFailureIsRetriedThenSucceedsWithoutRollback() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        doThrow(new SignupException(ErrorCodes.DB_FAILURE, true, null))
                .doThrow(new SignupException(ErrorCodes.THROTTLED, true, null))
                .doNothing().when(profiles).save(any(UserProfile.class));

        SignupResponse response = service.signup(request());

        assertEquals("uid-1", response.userId());
        verify(profiles, times(3)).save(any(UserProfile.class));
        assertEquals(List.of(100L, 200L), sleeps);
        verify(cognito, never()).deleteUser(anyString());
    }

    @Test
    void dbFailingThreeTimesRollsBackAndReportsLastError() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        doThrow(new SignupException(ErrorCodes.DB_FAILURE, true, null)).when(profiles).save(any(UserProfile.class));

        SignupException e = assertThrows(SignupException.class, () -> service.signup(request()));

        assertEquals(ErrorCodes.DB_FAILURE, e.getCode());
        verify(profiles, times(3)).save(any(UserProfile.class));
        verify(cognito).deleteUser(EMAIL);
    }

    @Test
    void persistentThrottlingSurfacesThrottledAfterRollback() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        doThrow(new SignupException(ErrorCodes.THROTTLED, true, null)).when(profiles).save(any(UserProfile.class));

        SignupException e = assertThrows(SignupException.class, () -> service.signup(request()));

        assertEquals(ErrorCodes.THROTTLED, e.getCode());
        verify(cognito).deleteUser(EMAIL);
    }

    @Test
    void nonRetryableDbFailureIsNotRetried() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        doThrow(new SignupException(ErrorCodes.DB_FAILURE, false, null)).when(profiles).save(any(UserProfile.class));

        assertThrows(SignupException.class, () -> service.signup(request()));

        verify(profiles, times(1)).save(any(UserProfile.class));
        assertEquals(List.of(), sleeps);
        verify(cognito).deleteUser(EMAIL);
    }

    @Test
    void failedRollbackDoesNotMaskOriginalError() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        doThrow(new SignupException(ErrorCodes.DB_FAILURE, false, null)).when(profiles).save(any(UserProfile.class));
        doThrow(new SignupException(ErrorCodes.COGNITO_FAILURE)).when(cognito).deleteUser(EMAIL);

        SignupException e = assertThrows(SignupException.class, () -> service.signup(request()));

        assertEquals(ErrorCodes.DB_FAILURE, e.getCode());
    }

    @Test
    void unexpectedRuntimeErrorBecomesInternalErrorAfterRollback() {
        when(cognito.createUser(EMAIL)).thenReturn("uid-1");
        doThrow(new IllegalStateException("surprise")).when(profiles).save(any(UserProfile.class));

        SignupException e = assertThrows(SignupException.class, () -> service.signup(request()));

        assertEquals(ErrorCodes.INTERNAL_ERROR, e.getCode());
        verify(cognito).deleteUser(EMAIL);
    }

    @Test
    void requestToStringRedactsEmailAndDoesNotLeakPassword() {
        SignupRequest trimmedRequest = request().withTrimmedEmail();
        assertTrue(trimmedRequest.toString().contains("email=A***@Example.com"));
        assertFalse(trimmedRequest.toString().contains(EMAIL));
        assertFalse(trimmedRequest.toString().contains("Str0ng"));
    }

    @Test
    void normalizeGenderCapitalizes() {
        assertEquals("Male", SignupService.normalizeGender("MALE"));
        assertEquals("Other", SignupService.normalizeGender(" oThEr "));
        assertNull(SignupService.normalizeGender(null));
    }
}
