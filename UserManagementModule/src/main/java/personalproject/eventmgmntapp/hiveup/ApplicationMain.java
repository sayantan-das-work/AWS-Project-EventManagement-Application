package personalproject.eventmgmntapp.hiveup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import personalproject.eventmgmntapp.hiveup.signup.config.AppConfig;
import personalproject.eventmgmntapp.hiveup.signup.config.AwsClientFactory;
import personalproject.eventmgmntapp.hiveup.signup.config.ServerSettings;
import personalproject.eventmgmntapp.hiveup.signup.config.SsmConfigLoader;
import personalproject.eventmgmntapp.hiveup.signup.http.HttpServerBootstrap;
import personalproject.eventmgmntapp.hiveup.signup.repository.AwsCognitoUserRepository;
import personalproject.eventmgmntapp.hiveup.signup.repository.DynamoDbUserProfileRepository;
import personalproject.eventmgmntapp.hiveup.signup.service.SignupService;
import personalproject.eventmgmntapp.hiveup.signup.util.RetryExecutor;
import personalproject.eventmgmntapp.hiveup.signup.validation.RuleLoader;
import personalproject.eventmgmntapp.hiveup.signup.validation.SignupValidator;

import java.time.Clock;

/** Application entry point: loads config from SSM, wires the signup service and starts the HTTP server. */
public class ApplicationMain {
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationMain.class);

    /**
     * Starts the server. Requires env vars {@code SSM_PARAM_PREFIX}, {@code SSM_PARAM_USER_POOL_ID},
     * {@code SSM_PARAM_REGION} and {@code SSM_PARAM_TABLE_NAME}; {@code PORT} is optional (default 8080).
     *
     * @param args unused
     */
    public static void main(String[] args) {
        try {
            ServerSettings settings = ServerSettings.fromEnv(System.getenv());
            AppConfig config = new SsmConfigLoader(AwsClientFactory.ssm(), settings.paramNames())
                    .load(settings.ssmPrefix());

            Clock clock = Clock.systemUTC();
            SignupService service = new SignupService(
                    new SignupValidator(RuleLoader.loadDefault(), clock),
                    new AwsCognitoUserRepository(AwsClientFactory.cognito(config), config.userPoolId()),
                    new DynamoDbUserProfileRepository(AwsClientFactory.dynamo(config), config.tableName()),
                    RetryExecutor.standard(),
                    clock);
            HttpServerBootstrap.start(settings.port(), service);
        } catch (RuntimeException e) {
            LOG.error("Startup failed: {}", e.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
