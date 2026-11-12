package personalproject.eventmgmntapp.hiveup.signup.config;

import org.junit.jupiter.api.Test;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerSettingsTest {

    /** Valid environment; tests override or remove entries. */
    private static Map<String, String> env(String... extra) {
        Map<String, String> env = new HashMap<>();
        env.put("SSM_PARAM_PREFIX", "/event-management/auth/");
        env.put("SSM_PARAM_USER_POOL_ID", "user-pool-id");
        env.put("SSM_PARAM_REGION", "AWS_REGION");
        env.put("SSM_PARAM_TABLE_NAME", "DYNAMODB_TABLE_NAME");
        for (int i = 0; i < extra.length; i += 2) {
            if (extra[i + 1] == null) {
                env.remove(extra[i]);
            } else {
                env.put(extra[i], extra[i + 1]);
            }
        }
        return env;
    }

    @Test
    void usesDefaultPortWhenNotSet() {
        ServerSettings s = ServerSettings.fromEnv(env());
        assertEquals(8080, s.port());
        assertEquals("/event-management/auth/", s.ssmPrefix());
    }

    @Test
    void carriesParameterNamesFromEnvironment() {
        SsmParamNames names = ServerSettings.fromEnv(env("SSM_PARAM_REGION", "custom-region")).paramNames();
        assertEquals("user-pool-id", names.userPoolId());
        assertEquals("custom-region", names.region());
        assertEquals("DYNAMODB_TABLE_NAME", names.tableName());
    }

    @Test
    void readsCustomPort() {
        assertEquals(9090, ServerSettings.fromEnv(env("PORT", " 9090 ")).port());
    }

    @Test
    void missingOrInvalidPrefixFails() {
        assertConfigFailure(env("SSM_PARAM_PREFIX", null));
        assertConfigFailure(env("SSM_PARAM_PREFIX", " "));
        assertConfigFailure(env("SSM_PARAM_PREFIX", "no-leading-slash"));
    }

    @Test
    void missingParameterNameVariableFails() {
        assertConfigFailure(env("SSM_PARAM_USER_POOL_ID", null));
        assertConfigFailure(env("SSM_PARAM_REGION", null));
        assertConfigFailure(env("SSM_PARAM_TABLE_NAME", null));
    }

    @Test
    void invalidPortFails() {
        assertConfigFailure(env("PORT", "abc"));
        assertConfigFailure(env("PORT", "0"));
        assertConfigFailure(env("PORT", "70000"));
    }

    private static void assertConfigFailure(Map<String, String> env) {
        SignupException e = assertThrows(SignupException.class, () -> ServerSettings.fromEnv(env));
        assertEquals(ErrorCodes.CONFIG_LOAD_FAILURE, e.getCode());
    }
}
