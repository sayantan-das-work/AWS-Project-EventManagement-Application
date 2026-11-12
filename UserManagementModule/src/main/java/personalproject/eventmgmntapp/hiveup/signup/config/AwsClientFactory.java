package personalproject.eventmgmntapp.hiveup.signup.config;

import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ssm.SsmClient;

/** Builds AWS SDK clients using the default credentials provider chain. */
public final class AwsClientFactory {
    private AwsClientFactory() {
    }

    /** @return SSM client using the default region provider chain */
    public static SsmClient ssm() {
        return SsmClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /**
     * @param config application configuration
     * @return Cognito client for the configured region
     */
    public static CognitoIdentityProviderClient cognito(AppConfig config) {
        return CognitoIdentityProviderClient.builder().region(Region.of(config.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /**
     * @param config application configuration
     * @return DynamoDB client for the configured region
     */
    public static DynamoDbClient dynamo(AppConfig config) {
        return DynamoDbClient.builder().region(Region.of(config.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }
}
