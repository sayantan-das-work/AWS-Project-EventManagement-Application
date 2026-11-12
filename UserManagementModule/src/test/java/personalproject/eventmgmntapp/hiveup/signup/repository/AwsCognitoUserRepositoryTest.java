package personalproject.eventmgmntapp.hiveup.signup.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.LimitExceededException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AwsCognitoUserRepositoryTest {
    private static final String POOL = "ap-south-1_pool";

    @Mock
    private CognitoIdentityProviderClient client;
    private AwsCognitoUserRepository repo;

    @BeforeEach
    void setUp() {
        repo = new AwsCognitoUserRepository(client, POOL);
    }

    private static AdminCreateUserResponse responseWithSub(String sub) {
        return AdminCreateUserResponse.builder().user(UserType.builder().attributes(
                AttributeType.builder().name("email").value("a@b.com").build(),
                AttributeType.builder().name("sub").value(sub).build()).build()).build();
    }

    @Test
    void createUserSendsEmailUnverifiedSuppressedAndReturnsSub() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(responseWithSub("sub-123"));

        String userId = repo.createUser("Asha@Example.com");

        assertEquals("sub-123", userId);
        ArgumentCaptor<AdminCreateUserRequest> captor = ArgumentCaptor.forClass(AdminCreateUserRequest.class);
        verify(client).adminCreateUser(captor.capture());
        AdminCreateUserRequest req = captor.getValue();
        assertEquals(POOL, req.userPoolId());
        assertEquals("Asha@Example.com", req.username());
        assertEquals(MessageActionType.SUPPRESS, req.messageAction());
        Map<String, String> attrs = req.userAttributes().stream()
                .collect(Collectors.toMap(AttributeType::name, AttributeType::value));
        assertEquals(Map.of("email", "Asha@Example.com", "email_verified", "false"), attrs);
        assertTrue(req.temporaryPassword() == null, "no temporary password should be supplied");
    }

    @Test
    void createUserWithoutSubInResponseFails() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenReturn(AdminCreateUserResponse.builder()
                .user(UserType.builder().attributes(AttributeType.builder().name("email").value("x").build()).build()).build());

        assertCode(ErrorCodes.COGNITO_FAILURE, () -> repo.createUser("a@b.com"));
    }

    @Test
    void duplicateEmailMapsToEmailAlreadyExists() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class))).thenThrow(UsernameExistsException.builder().build());
        assertCode(ErrorCodes.EMAIL_ALREADY_EXISTS, () -> repo.createUser("a@b.com"));
    }

    @Test
    void createUserThrottlingAndOtherFailuresAreMapped() {
        when(client.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(TooManyRequestsException.builder().build())
                .thenThrow(LimitExceededException.builder().build())
                .thenThrow(CognitoIdentityProviderException.builder().message("boom").build())
                .thenThrow(SdkClientException.create("network"));

        assertCode(ErrorCodes.THROTTLED, () -> repo.createUser("a@b.com"));
        assertCode(ErrorCodes.THROTTLED, () -> repo.createUser("a@b.com"));
        assertCode(ErrorCodes.COGNITO_FAILURE, () -> repo.createUser("a@b.com"));
        assertCode(ErrorCodes.COGNITO_FAILURE, () -> repo.createUser("a@b.com"));
    }

    @Test
    void setPermanentPasswordIsPermanent() {
        repo.setPermanentPassword("a@b.com", "Secret#123");

        ArgumentCaptor<AdminSetUserPasswordRequest> captor = ArgumentCaptor.forClass(AdminSetUserPasswordRequest.class);
        verify(client).adminSetUserPassword(captor.capture());
        AdminSetUserPasswordRequest req = captor.getValue();
        assertEquals(POOL, req.userPoolId());
        assertEquals("a@b.com", req.username());
        assertEquals("Secret#123", req.password());
        assertEquals(Boolean.TRUE, req.permanent());
    }

    @Test
    void setPermanentPasswordMapsInvalidPasswordAndOtherFailures() {
        when(client.adminSetUserPassword(any(AdminSetUserPasswordRequest.class)))
                .thenThrow(InvalidPasswordException.builder().message("policy").build())
                .thenThrow(CognitoIdentityProviderException.builder().message("boom").build());

        assertCode(ErrorCodes.PASSWORD_REJECTED, () -> repo.setPermanentPassword("a@b.com", "x"));
        assertCode(ErrorCodes.COGNITO_FAILURE, () -> repo.setPermanentPassword("a@b.com", "x"));
    }

    @Test
    void addToGroupSendsGroupAndMapsFailures() {
        repo.addToGroup("a@b.com", "public");

        ArgumentCaptor<AdminAddUserToGroupRequest> captor = ArgumentCaptor.forClass(AdminAddUserToGroupRequest.class);
        verify(client).adminAddUserToGroup(captor.capture());
        assertEquals("public", captor.getValue().groupName());
        assertEquals("a@b.com", captor.getValue().username());
        assertEquals(POOL, captor.getValue().userPoolId());

        when(client.adminAddUserToGroup(any(AdminAddUserToGroupRequest.class)))
                .thenThrow(CognitoIdentityProviderException.builder().message("boom").build());
        assertCode(ErrorCodes.COGNITO_FAILURE, () -> repo.addToGroup("a@b.com", "public"));
    }

    @Test
    void deleteUserSendsRequestAndMapsFailures() {
        repo.deleteUser("a@b.com");

        ArgumentCaptor<AdminDeleteUserRequest> captor = ArgumentCaptor.forClass(AdminDeleteUserRequest.class);
        verify(client).adminDeleteUser(captor.capture());
        assertEquals("a@b.com", captor.getValue().username());
        assertEquals(POOL, captor.getValue().userPoolId());

        when(client.adminDeleteUser(any(AdminDeleteUserRequest.class)))
                .thenThrow(CognitoIdentityProviderException.builder().message("boom").build());
        assertCode(ErrorCodes.COGNITO_FAILURE, () -> repo.deleteUser("a@b.com"));
    }

    private static void assertCode(String expected, Runnable action) {
        SignupException e = assertThrows(SignupException.class, action::run);
        assertEquals(expected, e.getCode());
    }
}
