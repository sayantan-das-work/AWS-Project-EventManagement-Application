package personalproject.eventmgmntapp.hiveup.signup.config;

/**
 * Runtime configuration fetched from SSM Parameter Store.
 *
 * @param userPoolId Cognito user pool id
 * @param region     AWS region of Cognito and DynamoDB
 * @param tableName  DynamoDB table for user profiles
 */
public record AppConfig(String userPoolId, String region, String tableName) {
    /** @return text without any configuration values */
    @Override
    public String toString() {
        return "AppConfig[redacted]";
    }
}
