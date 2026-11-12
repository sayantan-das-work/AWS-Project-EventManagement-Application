package personalproject.eventmgmntapp.hiveup.signup.config;

import org.junit.jupiter.api.Test;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsmParamNamesTest {

    private static Map<String, String> env() {
        Map<String, String> env = new HashMap<>();
        env.put("SSM_PARAM_USER_POOL_ID", "user-pool-id");
        env.put("SSM_PARAM_REGION", "AWS_REGION");
        env.put("SSM_PARAM_TABLE_NAME", "DYNAMODB_TABLE_NAME");
        return env;
    }

    @Test
    void readsAllThreeNamesFromEnvironmentAndTrims() {
        Map<String, String> env = env();
        env.put("SSM_PARAM_REGION", "  region-param ");

        SsmParamNames names = SsmParamNames.fromEnv(env);

        assertEquals("user-pool-id", names.userPoolId());
        assertEquals("region-param", names.region());
        assertEquals("DYNAMODB_TABLE_NAME", names.tableName());
    }

    @Test
    void anyMissingOrBlankVariableFails() {
        for (String variable : new String[]{SsmParamNames.ENV_USER_POOL_ID, SsmParamNames.ENV_REGION,
                SsmParamNames.ENV_TABLE_NAME}) {
            Map<String, String> missing = env();
            missing.remove(variable);
            assertConfigFailure(missing);

            Map<String, String> blank = env();
            blank.put(variable, "  ");
            assertConfigFailure(blank);
        }
    }

    @Test
    void emptyEnvironmentFails() {
        assertConfigFailure(Map.of());
    }

    private static void assertConfigFailure(Map<String, String> env) {
        SignupException e = assertThrows(SignupException.class, () -> SsmParamNames.fromEnv(env));
        assertEquals(ErrorCodes.CONFIG_LOAD_FAILURE, e.getCode());
    }
}
