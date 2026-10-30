package br.com.helpcsistemas.customeraccount.tenant.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TenantWebhookDomainTest {

    private static final TenantId TENANT = TenantId.of("tenant-a");

    @Test
    void signsTimestampAndBodyWithHmacSha256() {
        var signature = WebhookSignature.sign("segredo", 1_700_000_000L, "{}");

        assertThat(signature).startsWith("v1=").hasSize(3 + 64);
        assertThat(WebhookSignature.sign("segredo", 1_700_000_000L, "{}")).isEqualTo(signature);
        assertThat(WebhookSignature.sign("segredo", 1_700_000_001L, "{}")).isNotEqualTo(signature);
        assertThat(WebhookSignature.sign("outro", 1_700_000_000L, "{}")).isNotEqualTo(signature);
    }

    @Test
    void acceptsHttpsEndpoint() {
        var endpoint = WebhookEndpoint.of(TENANT, " https://app.example.com/hooks/ca?x=1 ");

        assertThat(endpoint.url().getHost()).isEqualTo("app.example.com");
        assertThat(endpoint.enabled()).isTrue();
        assertThat(endpoint.disabled().enabled()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "ftp://app.example.com/hook",
                "https://user:pass@app.example.com/hook",
                "https://app.example.com/hook#frag",
                "notaurl",
                "https:///sem-host"
            })
    void rejectsInvalidEndpoints(String raw) {
        assertThatThrownBy(() -> WebhookEndpoint.of(TENANT, raw))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code")
                .isEqualTo("INVALID_WEBHOOK_URL");
    }

    @Test
    void retriesWithGrowingDelaysUntilExhausted() {
        var policy = new DeliveryPolicy(8);

        assertThat(policy.delayAfter(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.delayAfter(2)).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.delayAfter(20)).isEqualTo(Duration.ofHours(6));
        assertThat(policy.exhausted(7)).isFalse();
        assertThat(policy.exhausted(8)).isTrue();
        assertThatThrownBy(() -> new DeliveryPolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
