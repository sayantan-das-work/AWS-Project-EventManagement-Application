package personalproject.eventmgmntapp.hiveup.signup.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Names of the SSM parameters (relative to the prefix) that hold the app configuration.
 * The names are read from environment variables so nothing is hard-coded.
 *
 * @param userPoolId parameter name holding the Cognito user pool id
 * @param region     parameter name holding the AWS region of Cognito and DynamoDB
 * @param tableName  parameter name holding the DynamoDB table name
 */
public record SsmParamNames(String userPoolId, String region, String tableName) {
    /** Environment variable holding the parameter name of the user pool id. */
    public static final String ENV_USER_POOL_ID = "SSM_PARAM_USER_POOL_ID";
    /** Environment variable holding the parameter name of the region. */
    public static final String ENV_REGION = "SSM_PARAM_REGION";
    /** Environment variable holding the parameter name of the table name. */
    public static final String ENV_TABLE_NAME = "SSM_PARAM_TABLE_NAME";

    private static final Logger LOG = LoggerFactory.getLogger(SsmParamNames.class);

    /**
     * Reads the three parameter names from an environment map; all are required.
     *
     * @param env environment variables (e.g. {@code System.getenv()})
     * @return parameter names (trimmed)
     * @throws SignupException CONFIG_LOAD_FAILURE if any variable is missing or blank
     */
    public static SsmParamNames fromEnv(Map<String, String> env) {
        List<String> missing = new ArrayList<>();
        String pool = read(env, ENV_USER_POOL_ID, missing);
        String region = read(env, ENV_REGION, missing);
        String table = read(env, ENV_TABLE_NAME, missing);
        if (!missing.isEmpty()) {
            LOG.error("Missing required environment variables: {}", missing);
            throw new SignupException(ErrorCodes.CONFIG_LOAD_FAILURE);
        }
        return new SsmParamNames(pool, region, table);
    }

    private static String read(Map<String, String> env, String variable, List<String> missing) {
        String value = env.get(variable);
        if (value == null || value.isBlank()) {
            missing.add(variable);
            return null;
        }
        return value.trim();
    }
}
