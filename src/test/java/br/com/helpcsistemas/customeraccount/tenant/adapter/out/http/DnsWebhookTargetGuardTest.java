package br.com.helpcsistemas.customeraccount.tenant.adapter.out.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.tenant.config.TenantWebhookProperties;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DnsWebhookTargetGuardTest {

    private static DnsWebhookTargetGuard guard(boolean allowInsecure, boolean allowPrivate) {
        return new DnsWebhookTargetGuard(
                new TenantWebhookProperties(allowInsecure, allowPrivate, Duration.ofSeconds(1), 3, 10));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "127.0.0.1",
                "10.1.2.3",
                "172.16.0.10",
                "192.168.0.1",
                "169.254.169.254",
                "100.64.0.1",
                "0.0.0.0",
                "224.0.0.1",
                "::1",
                "fe80::1",
                "fd00::1"
            })
    void classifiesInternalAddresses(String ip) throws UnknownHostException {
        assertThat(DnsWebhookTargetGuard.isInternal(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "100.128.0.1", "2001:4860:4860::8888"})
    void allowsPublicAddresses(String ip) throws UnknownHostException {
        assertThat(DnsWebhookTargetGuard.isInternal(InetAddress.getByName(ip))).isFalse();
    }

    @Test
    void blocksInternalTargetsAndPlainHttpByDefault() {
        var guard = guard(false, false);

        assertThatThrownBy(() -> guard.ensureAllowed(URI.create("https://127.0.0.1/hook")))
                .isInstanceOf(InvalidValueException.class)
                .extracting("code")
                .isEqualTo("WEBHOOK_TARGET_NOT_ALLOWED");
        assertThatThrownBy(() -> guard.ensureAllowed(URI.create("http://8.8.8.8/hook")))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void developmentFlagsRelaxTheChecks() {
        assertThatCode(() -> guard(true, true).ensureAllowed(URI.create("http://localhost:9999/hook")))
                .doesNotThrowAnyException();
    }
}
