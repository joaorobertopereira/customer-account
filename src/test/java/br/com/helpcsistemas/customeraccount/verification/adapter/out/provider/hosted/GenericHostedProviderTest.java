package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.hosted;

import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderTestSupport.credentials;
import static br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderTestSupport.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.helpcsistemas.customeraccount.verification.config.HostedProvidersProperties.Callback;
import br.com.helpcsistemas.customeraccount.verification.config.HostedProvidersProperties.Provider;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class GenericHostedProviderTest {

    private static final String BASE = "https://provider.example";
    private static final byte[] BODY = """
            {"eventId":"evt-1","sessionId":"sid-1","status":"approved","score":94,"reasons":[],"fraudSignals":[],"occurredAt":"2026-10-30T12:00:00Z"}
            """.strip().getBytes(StandardCharsets.UTF_8);

    @Test
    void createsHostedSessionWithConfiguredFieldMapping() {
        var builder = RestClient.builder().baseUrl(BASE);
        var server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GenericHostedProvider(
                ProviderId.of("legitimuz"),
                providerSettings(),
                builder.build(),
                credentials(Map.of("providers/legitimuz/default", Map.of("api_token", "token"))),
                JsonMapper.builder().build(),
                CLOCK);
        server.expect(requestTo(BASE + "/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andExpect(content().string(containsString("\"document\":\"52998224725\"")))
                .andExpect(content().string(containsString("\"reference\":")))
                .andRespond(withSuccess(
                        "{\"id\":\"sid-1\",\"url\":\"https://hosted.example/sid-1\",\"expiresAt\":\"2026-01-16T12:00:00Z\"}",
                        MediaType.APPLICATION_JSON));

        var session = provider.start(request(VerificationType.IDENTITY), null);

        assertThat(session.sessionId()).isEqualTo("sid-1");
        assertThat(session.url()).isEqualTo("https://hosted.example/sid-1");
        assertThat(session.expiresAt()).isEqualTo(CLOCK.instant().plus(Duration.ofDays(1)));
        assertThat(session.sharedFields()).containsExactly("cpf", "reference");
        server.verify();
    }

    @Test
    void parsesNormalizedCallbackAndAuthenticatesTimestampedHmac() throws Exception {
        var provider = provider();
        String timestamp = Long.toString(CLOCK.instant().getEpochSecond());
        var headers = Map.of(
                "x-timestamp",
                timestamp,
                "X-Signature",
                sign("webhook-key", timestamp + "." + new String(BODY, StandardCharsets.UTF_8)));

        assertThat(provider.authenticateCallback(headers, BODY, "tenant-a")).isTrue();
        var result = provider.parseCallback(BODY);
        assertThat(result.sessionId()).isEqualTo("sid-1");
        assertThat(result.providerEventId()).isEqualTo("evt-1");
        assertThat(result.status()).isEqualTo(HostedResultStatus.COMPLETED);
        assertThat(result.score()).isEqualTo(94);
        assertThat(result.rawResponse()).contains("evt-1");
    }

    @Test
    void rejectsTamperedOrStaleCallback() throws Exception {
        var provider = provider();
        String timestamp = Long.toString(CLOCK.instant().getEpochSecond());
        var validSignature = sign("webhook-key", timestamp + "." + new String(BODY, StandardCharsets.UTF_8));

        assertThat(provider.authenticateCallback(
                        Map.of("X-Timestamp", timestamp, "X-Signature", validSignature + "x"), BODY, "tenant-a"))
                .isFalse();
        assertThat(provider.authenticateCallback(
                        Map.of("X-Timestamp", "1", "X-Signature", validSignature), BODY, "tenant-a"))
                .isFalse();
    }

    @Test
    void rejectsUnknownCallbackStatus() {
        var provider = provider();
        byte[] unknown = "{\"sessionId\":\"sid-1\",\"status\":\"surprise\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider.parseCallback(unknown)).isInstanceOf(IllegalArgumentException.class);
    }

    private static GenericHostedProvider provider() {
        return new GenericHostedProvider(
                ProviderId.of("legitimuz"),
                providerSettings(),
                RestClient.builder().baseUrl(BASE).build(),
                credentials(Map.of(
                        "providers/legitimuz/default", Map.of("api_token", "token", "webhook_secret", "webhook-key"))),
                JsonMapper.builder().build(),
                CLOCK);
    }

    private static Provider providerSettings() {
        return new Provider(
                true,
                BASE,
                Duration.ofSeconds(2),
                "BEARER",
                null,
                "/sessions",
                Map.of("document", "cpf", "reference", "reference"),
                "/id",
                "/url",
                "/expiresAt",
                new Callback(
                        "X-Signature",
                        "X-Timestamp",
                        Duration.ofMinutes(5),
                        "/eventId",
                        "/sessionId",
                        "/status",
                        "/score",
                        "/reasons",
                        "/fraudSignals",
                        "/occurredAt",
                        Map.of("approved", HostedResultStatus.COMPLETED)));
    }

    private static String sign(String secret, String value) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "v1=" + Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
