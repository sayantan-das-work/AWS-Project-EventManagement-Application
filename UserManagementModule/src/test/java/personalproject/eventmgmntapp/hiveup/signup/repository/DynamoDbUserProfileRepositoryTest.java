package personalproject.eventmgmntapp.hiveup.signup.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import personalproject.eventmgmntapp.hiveup.signup.model.UserProfile;
import personalproject.eventmgmntapp.hiveup.signup.model.UserStatus;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDbUserProfileRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Mock
    private DynamoDbClient client;
    private DynamoDbUserProfileRepository repo;

    @BeforeEach
    void setUp() {
        repo = new DynamoDbUserProfileRepository(client, "users");
    }

    private static UserProfile profile(String gender, String profession) {
        return new UserProfile("uid-1", "Asha", "Rao", gender, "1995-04-12", "Asha@Example.com", profession,
                "+919876543210", NOW, UserStatus.UNVERIFIED, NOW.plusSeconds(86400).getEpochSecond());
    }

    @Test
    void savesAllAttributesWithConditionalPut() {
        repo.save(profile("Female", "Engineer"));

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(client).putItem(captor.capture());
        PutItemRequest req = captor.getValue();
        assertEquals("users", req.tableName());
        assertEquals("attribute_not_exists(user_id)", req.conditionExpression());

        Map<String, AttributeValue> item = req.item();
        assertEquals("uid-1", item.get("user_id").s());
        assertEquals("Asha", item.get("first_name").s());
        assertEquals("Rao", item.get("last_name").s());
        assertEquals("Female", item.get("gender").s());
        assertEquals("1995-04-12", item.get("date_of_birth").s());
        assertEquals("Asha@Example.com", item.get("email").s());
        assertEquals("Engineer", item.get("profession").s());
        assertEquals("+919876543210", item.get("contact_number").s());
        assertEquals("2026-11-11T10:00:00Z", item.get("created_at").s());
        assertEquals("2026-11-11T10:00:00Z", item.get("updated_at").s());
        assertEquals("UNVERIFIED", item.get("status").s());
        assertEquals(Long.toString(NOW.plusSeconds(86400).getEpochSecond()), item.get("expires_at").n());
        assertFalse(item.containsKey("password"));
    }

    @Test
    void omitsAbsentOptionalAttributes() {
        Map<String, AttributeValue> item = DynamoDbUserProfileRepository.toItem(profile(null, null));
        assertFalse(item.containsKey("gender"));
        assertFalse(item.containsKey("profession"));
        assertNotNull(item.get("user_id"));
    }

    @Test
    void expiryIsTwentyFourHoursAfterCreation() {
        Map<String, AttributeValue> item = DynamoDbUserProfileRepository.toItem(profile("Male", null));
        long expires = Long.parseLong(item.get("expires_at").n());
        assertEquals(NOW.getEpochSecond() + 24 * 3600, expires);
    }

    @Test
    void conditionalCheckFailureIsNotRetryable() {
        when(client.putItem(any(PutItemRequest.class))).thenThrow(ConditionalCheckFailedException.builder().build());
        SignupException e = assertThrows(SignupException.class, () -> repo.save(profile(null, null)));
        assertEquals(ErrorCodes.DB_FAILURE, e.getCode());
        assertFalse(e.isRetryable());
    }

    @Test
    void throttlingIsRetryable() {
        when(client.putItem(any(PutItemRequest.class))).thenThrow(ProvisionedThroughputExceededException.builder()
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("ProvisionedThroughputExceededException").build())
                .statusCode(400).build());
        SignupException e = assertThrows(SignupException.class, () -> repo.save(profile(null, null)));
        assertEquals(ErrorCodes.THROTTLED, e.getCode());
        assertTrue(e.isRetryable());
    }

    @Test
    void serverErrorIsRetryableAndClientErrorIsNot() {
        when(client.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().statusCode(500)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("InternalServerError").build()).build())
                .thenThrow(DynamoDbException.builder().statusCode(400)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("ValidationException").build()).build());

        SignupException serverError = assertThrows(SignupException.class, () -> repo.save(profile(null, null)));
        assertEquals(ErrorCodes.DB_FAILURE, serverError.getCode());
        assertTrue(serverError.isRetryable());

        SignupException clientError = assertThrows(SignupException.class, () -> repo.save(profile(null, null)));
        assertEquals(ErrorCodes.DB_FAILURE, clientError.getCode());
        assertFalse(clientError.isRetryable());
    }

    @Test
    void networkFailureIsRetryable() {
        when(client.putItem(any(PutItemRequest.class))).thenThrow(SdkClientException.create("timeout"));
        SignupException e = assertThrows(SignupException.class, () -> repo.save(profile(null, null)));
        assertEquals(ErrorCodes.DB_FAILURE, e.getCode());
        assertTrue(e.isRetryable());
    }
}
