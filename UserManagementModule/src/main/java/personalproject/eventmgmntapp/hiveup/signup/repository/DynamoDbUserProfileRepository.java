package personalproject.eventmgmntapp.hiveup.signup.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import personalproject.eventmgmntapp.hiveup.signup.model.UserProfile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

/** {@link UserProfileRepository} backed by DynamoDB (partition key {@code user_id}). */
public class DynamoDbUserProfileRepository implements UserProfileRepository {
    private static final Logger LOG = LoggerFactory.getLogger(DynamoDbUserProfileRepository.class);

    private final DynamoDbClient client;
    private final String tableName;

    /**
     * @param client    DynamoDB client
     * @param tableName table name
     */
    public DynamoDbUserProfileRepository(DynamoDbClient client, String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    @Override
    public void save(UserProfile profile) {
        try {
            LOG.info("Storing profile in database for userId={}", profile.userId());
            client.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(toItem(profile))
                    .conditionExpression("attribute_not_exists(user_id)")
                    .build());
        } catch (ConditionalCheckFailedException e) {
            LOG.error("DynamoDB put rejected, userId already exists: {}", profile.userId());
            throw new SignupException(ErrorCodes.DB_FAILURE, false, e);
        } catch (SdkException e) {
            throw mapFailure(e);
        }
    }

    /** Builds the item; optional attributes are omitted when null. */
    static Map<String, AttributeValue> toItem(UserProfile p) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("user_id", s(p.userId()));
        item.put("first_name", s(p.firstName()));
        item.put("last_name", s(p.lastName()));
        item.put("date_of_birth", s(p.dateOfBirth()));
        item.put("email", s(p.email()));
        item.put("contact_number", s(p.contactNumber()));
        item.put("created_at", s(p.createdAt().toString()));
        item.put("updated_at", s(p.createdAt().toString()));
        item.put("status", s(p.status().name()));
        item.put("expires_at", AttributeValue.builder().n(Long.toString(p.expiresAtEpochSeconds())).build());
        if (p.gender() != null) {
            item.put("gender", s(p.gender()));
        }
        if (p.profession() != null) {
            item.put("profession", s(p.profession()));
        }
        return item;
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }

    /** Throttling and 5xx/network errors are retryable; everything else is not. */
    private static SignupException mapFailure(SdkException e) {
        String detail = e instanceof AwsServiceException ase && ase.awsErrorDetails() != null
                ? ase.awsErrorDetails().errorCode() : e.getClass().getSimpleName();
        LOG.error("DynamoDB PutItem failed: {}", detail);
        if (e instanceof AwsServiceException ase) {
            if (ase.isThrottlingException()) {
                return new SignupException(ErrorCodes.THROTTLED, true, e);
            }
            return new SignupException(ErrorCodes.DB_FAILURE, ase.statusCode() >= 500, e);
        }
        return new SignupException(ErrorCodes.DB_FAILURE, e instanceof SdkClientException, e);
    }
}
