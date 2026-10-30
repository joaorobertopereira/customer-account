package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.zerobounce;

import static br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderTestSupport.credentials;
import static br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderCredentialsMissingException;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class ZeroBounceProviderTest {

    private static final String BASE = "https://api.zerobounce.net";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final JsonMapper json = JsonMapper.builder().build();

    private ZeroBounceProvider provider(Map<String, Map<String, String>> secrets) {
        return new ZeroBounceProvider(builder.build(), credentials(secrets), json);
    }

    @Test
    void normalizesProviderStatusIntoStandardResult() {
        server.expect(requestTo(startsWith(BASE + "/v2/validate")))
                .andExpect(queryParam("api_key", "k1"))
                // Variáveis de URI são codificadas por completo (@ -> %40).
                .andExpect(queryParam("email", "maria%40example.com"))
                .andRespond(withSuccess(
                        "{\"status\":\"do_not_mail\",\"sub_status\":\"disposable\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/v2/validate")))
                .andRespond(withSuccess("{\"status\":\"valid\",\"sub_status\":\"\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/v2/validate")))
                .andRespond(withSuccess("{\"status\":\"catch-all\"}", MediaType.APPLICATION_JSON));
        var provider = provider(Map.of("providers/zerobounce/default", Map.of("api_key", "k1")));

        var rejected = provider.verify(request(VerificationType.EMAIL_QUALITY));
        var approved = provider.verify(request(VerificationType.EMAIL_QUALITY));
        var inconclusive = provider.verify(request(VerificationType.EMAIL_QUALITY));

        assertThat(rejected.decision()).isEqualTo(ProviderDecision.REJECTED);
        assertThat(rejected.reasonCodes()).containsExactly("STATUS_DO_NOT_MAIL", "SUB_STATUS_DISPOSABLE");
        assertThat(approved.decision()).isEqualTo(ProviderDecision.APPROVED);
        assertThat(approved.reasonCodes()).isEmpty();
        assertThat(inconclusive.decision()).isEqualTo(ProviderDecision.INCONCLUSIVE);
        server.verify();
    }

    @Test
    void tenantSpecificCredentialTakesPrecedence() {
        server.expect(requestTo(startsWith(BASE + "/v2/validate")))
                .andExpect(queryParam("api_key", "tenant-key"))
                .andRespond(withSuccess("{\"status\":\"valid\"}", MediaType.APPLICATION_JSON));
        var provider = provider(Map.of(
                "providers/zerobounce/default", Map.of("api_key", "k1"),
                "providers/zerobounce/tenants/tenant-a", Map.of("api_key", "tenant-key")));

        assertThat(provider.verify(request(VerificationType.EMAIL_QUALITY)).decision())
                .isEqualTo(ProviderDecision.APPROVED);
        server.verify();
    }

    @Test
    void failsTechnicallyWithoutCredentials() {
        var provider = provider(Map.of());

        assertThatThrownBy(() -> provider.verify(request(VerificationType.EMAIL_QUALITY)))
                .isInstanceOf(ProviderCredentialsMissingException.class);
    }
}
