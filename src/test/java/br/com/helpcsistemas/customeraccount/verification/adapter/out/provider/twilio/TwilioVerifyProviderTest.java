package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.twilio;

import static br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderTestSupport.credentials;
import static br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class TwilioVerifyProviderTest {

    private static final String BASE = "https://verify.twilio.com";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final TwilioVerifyProvider provider = new TwilioVerifyProvider(
            builder.build(),
            credentials(Map.of(
                    "providers/twilio-verify/default",
                    Map.of("account_sid", "AC1", "auth_token", "tok", "service_sid", "VA1"))),
            JsonMapper.builder().build());

    private static String basicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString("AC1:tok".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void sendsSmsChallengeAndReturnsTwilioSid() {
        server.expect(requestTo(BASE + "/v2/Services/VA1/Verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth()))
                .andExpect(content().string(containsString("Channel=sms")))
                .andRespond(withSuccess("{\"sid\":\"VE1\",\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        var result = provider.verify(request(VerificationType.MOBILE_PHONE_OWNERSHIP));

        assertThat(result.decision()).isEqualTo(ProviderDecision.CHALLENGE_SENT);
        assertThat(result.transactionId()).isEqualTo("VE1");
        server.verify();
    }

    @Test
    void checksTheCodeWithTwilio() {
        server.expect(requestTo(BASE + "/v2/Services/VA1/VerificationCheck"))
                .andExpect(content().string(containsString("Code=123456")))
                .andRespond(withSuccess("{\"sid\":\"VE1\",\"status\":\"approved\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v2/Services/VA1/VerificationCheck"))
                .andRespond(withSuccess("{\"sid\":\"VE1\",\"status\":\"pending\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/v2/Services/VA1/VerificationCheck")).andRespond(withResourceNotFound());

        var request = request(VerificationType.MOBILE_PHONE_OWNERSHIP);

        assertThat(provider.confirm(request, "VE1", "123456").decision()).isEqualTo(ProviderDecision.APPROVED);
        assertThat(provider.confirm(request, "VE1", "000000").reasonCodes()).containsExactly("INVALID_CODE");
        assertThat(provider.confirm(request, "VE1", "123456").reasonCodes()).containsExactly("CHALLENGE_NOT_FOUND");
        server.verify();
    }
}
