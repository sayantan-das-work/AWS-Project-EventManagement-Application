package personalproject.eventmgmntapp.hiveup.signup.config;

import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;

import java.util.Map;

/**
 * Bootstrap settings read from environment variables.
 *
 * @param port      HTTP port
 * @param ssmPrefix  SSM parameter path prefix, e.g. {@code /event-management/auth/}
 * @param paramNames names of the SSM parameters to read under the prefix
 */
public record ServerSettings(int port, String ssmPrefix, SsmParamNames paramNames) {
    /** Environment variable holding the SSM prefix. */
    public static final String ENV_PREFIX = "SSM_PARAM_PREFIX";
    /** Environment variable holding the HTTP port. */
    public static final String ENV_PORT = "PORT";
    /** Port used when {@code PORT} is not set. */
    public static final int DEFAULT_PORT = 8080;

    /**
     * Reads settings from an environment map.
     *
     * @param env environment variables (e.g. {@code System.getenv()})
     * @return settings
     * @throws SignupException CONFIG_LOAD_FAILURE if the prefix or any SSM parameter-name variable is missing,
     *                         or the port is invalid
     */
    public static ServerSettings fromEnv(Map<String, String> env) {
        String prefix = env.get(ENV_PREFIX);
        if (prefix == null || prefix.isBlank() || !prefix.startsWith("/")) {
            throw new SignupException(ErrorCodes.CONFIG_LOAD_FAILURE);
        }
        String rawPort = env.get(ENV_PORT);
        int port = DEFAULT_PORT;
        if (rawPort != null && !rawPort.isBlank()) {
            try {
                port = Integer.parseInt(rawPort.trim());
            } catch (NumberFormatException e) {
                throw new SignupException(ErrorCodes.CONFIG_LOAD_FAILURE, false, e);
            }
            if (port < 1 || port > 65535) {
                throw new SignupException(ErrorCodes.CONFIG_LOAD_FAILURE);
            }
        }
        return new ServerSettings(port, prefix.trim(), SsmParamNames.fromEnv(env));
    }
}
