package personalproject.eventmgmntapp.hiveup.signup.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personalproject.eventmgmntapp.hiveup.signup.error.ErrorCodes;
import personalproject.eventmgmntapp.hiveup.signup.error.SignupException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsmConfigLoaderTest {
    private static final String PREFIX = "/event-management/auth/";
    private static final SsmParamNames NAMES = new SsmParamNames("user-pool-id", "AWS_REGION", "DYNAMODB_TABLE_NAME");

    @Mock
    private SsmClient ssm;

    private static Parameter param(String key, String value) {
        return Parameter.builder().name("/event-management/auth/" + key).value(value).build();
    }

    @Test
    void loadsAllRequiredParametersDecrypted() {
        when(ssm.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(
                GetParametersByPathResponse.builder().parameters(List.of(
                        param("user-pool-id", "ap-south-1_abc"),
                        param("AWS_REGION", "ap-south-1"),
                        param("DYNAMODB_TABLE_NAME", "users"),
                        param("UNRELATED", "ignored"))).build());

        AppConfig config = new SsmConfigLoader(ssm, NAMES).load(PREFIX);

        assertEquals("ap-south-1_abc", config.userPoolId());
        assertEquals("ap-south-1", config.region());
        assertEquals("users", config.tableName());

        ArgumentCaptor<GetParametersByPathRequest> captor = ArgumentCaptor.forClass(GetParametersByPathRequest.class);
        verify(ssm).getParametersByPath(captor.capture());
        assertEquals("/event-management/auth", captor.getValue().path());
        assertEquals(Boolean.TRUE, captor.getValue().withDecryption());
        assertNull(captor.getValue().nextToken());
    }

    @Test
    void usesTheConfiguredParameterNamesNotFixedOnes() {
        when(ssm.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(
                GetParametersByPathResponse.builder().parameters(List.of(
                        param("my-pool", "pool-1"), param("my-region", "eu-west-1"), param("my-table", "tbl"),
                        param("user-pool-id", "wrong"))).build());

        AppConfig config = new SsmConfigLoader(ssm, new SsmParamNames("my-pool", "my-region", "my-table")).load(PREFIX);

        assertEquals("pool-1", config.userPoolId());
        assertEquals("eu-west-1", config.region());
        assertEquals("tbl", config.tableName());
    }

    @Test
    void followsPaginationTokens() {
        when(ssm.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenReturn(GetParametersByPathResponse.builder().nextToken("page2")
                        .parameters(List.of(param("user-pool-id", "pool"), param("AWS_REGION", "r"))).build())
                .thenReturn(GetParametersByPathResponse.builder()
                        .parameters(List.of(param("DYNAMODB_TABLE_NAME", "t"))).build());

        AppConfig config = new SsmConfigLoader(ssm, NAMES).load(PREFIX);

        assertEquals("t", config.tableName());
        ArgumentCaptor<GetParametersByPathRequest> captor = ArgumentCaptor.forClass(GetParametersByPathRequest.class);
        verify(ssm, times(2)).getParametersByPath(captor.capture());
        assertEquals("page2", captor.getAllValues().get(1).nextToken());
    }

    @Test
    void missingParameterFailsFast() {
        when(ssm.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(
                GetParametersByPathResponse.builder().parameters(List.of(
                        param("user-pool-id", "pool"), param("AWS_REGION", "r"))).build());

        SignupException e = assertThrows(SignupException.class, () -> new SsmConfigLoader(ssm, NAMES).load(PREFIX));
        assertEquals(ErrorCodes.CONFIG_LOAD_FAILURE, e.getCode());
    }

    @Test
    void blankParameterValueCountsAsMissing() {
        when(ssm.getParametersByPath(any(GetParametersByPathRequest.class))).thenReturn(
                GetParametersByPathResponse.builder().parameters(List.of(
                        param("user-pool-id", " "), param("AWS_REGION", "r"), param("DYNAMODB_TABLE_NAME", "t"))).build());

        assertThrows(SignupException.class, () -> new SsmConfigLoader(ssm, NAMES).load(PREFIX));
    }

    @Test
    void ssmFailureBecomesConfigFailure() {
        when(ssm.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenThrow(SdkClientException.create("no network"));

        SignupException e = assertThrows(SignupException.class, () -> new SsmConfigLoader(ssm, NAMES).load(PREFIX));
        assertEquals(ErrorCodes.CONFIG_LOAD_FAILURE, e.getCode());
    }

    @Test
    void appConfigToStringHidesValues() {
        assertFalse(new AppConfig("pool", "r", "t").toString().contains("pool"));
    }
}
