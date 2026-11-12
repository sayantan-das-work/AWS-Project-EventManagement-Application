package personalproject.eventmgmntapp.hiveup.signup.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Loads {@link AppConfig} from SSM Parameter Store using a path prefix. */
public class SsmConfigLoader {
    private static final Logger LOG = LoggerFactory.getLogger(SsmConfigLoader.class);

    private final SsmClient ssm;
    private final SsmParamNames names;

    /**
     * @param ssm   SSM client
     * @param names names of the parameters to read (from environment variables)
     */
    public SsmConfigLoader(SsmClient ssm, SsmParamNames names) {
        this.ssm = ssm;
        this.names = names;
    }

    /**
     * Fetches all parameters under the prefix (decrypted, paginated) and builds the config.
     *
     * @param prefix SSM path prefix, e.g. {@code /event-management/auth/}
     * @return configuration
     * @throws SignupException CONFIG_LOAD_FAILURE if SSM fails or a required parameter is missing
     */
    public AppConfig load(String prefix) {
        String path = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        Map<String, String> values = new HashMap<>();
        try {
            String nextToken = null;
            do {
                GetParametersByPathResponse response = ssm.getParametersByPath(
                        GetParametersByPathRequest.builder().path(path)
                                .recursive(false)
                                .withDecryption(true)
                                .nextToken(nextToken)
                                .build()
                );
                for (Parameter p : response.parameters()) {
                    values.put(p.name().substring(p.name().lastIndexOf('/') + 1), p.value());
                }
                nextToken = response.nextToken();
            } while (nextToken != null);
        } catch (SdkException e) {
            // Startup-only path with no user data, so the SDK message (credentials, region, network or
            // access-denied reason) is safe to log and is what makes this failure diagnosable.
            LOG.error("Failed to read SSM parameters under '{}': {}: {}", path, e.getClass().getSimpleName(),
                    e.getMessage());
            throw new SignupException(ErrorCodes.CONFIG_LOAD_FAILURE, false, e);
        }

        List<String> missing = List.of(names.userPoolId(), names.region(), names.tableName()).stream()
                .filter(k -> values.get(k) == null || values.get(k).isBlank()).toList();
        if (!missing.isEmpty()) {
            LOG.error("Missing required SSM parameters under '{}': {}", path, missing);
            throw new SignupException(ErrorCodes.CONFIG_LOAD_FAILURE);
        }
        return new AppConfig(values.get(names.userPoolId()), values.get(names.region()),
                values.get(names.tableName()));
    }
}
