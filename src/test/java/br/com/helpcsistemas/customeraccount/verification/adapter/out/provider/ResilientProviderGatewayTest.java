package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderUnavailableException;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientProviderGatewayTest {

    private final AtomicInteger calls = new AtomicInteger();
    private final ProviderRequest request =
            new ProviderRequest(TenantId.of("tenant-a"), VerificationId.newId(), VerificationType.LIVENESS, null);

    private ResilientProviderGateway gateway(VerificationProvider provider) {
        var circuitBreakers = CircuitBreakerRegistry.of(Map.of(
                ResilientProviderGateway.RESILIENCE_CONFIG,
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(2)
                        .minimumNumberOfCalls(2)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .build()));
        var retries = RetryRegistry.of(Map.of(
                ResilientProviderGateway.RESILIENCE_CONFIG,
                RetryConfig.custom()
                        .maxAttempts(2)
                        .waitDuration(Duration.ofMillis(1))
                        .build()));
        return new ResilientProviderGateway(List.of(provider), circuitBreakers, retries, ObservationRegistry.NOOP);
    }

    private VerificationProvider provider(boolean healthy) {
        return new VerificationProvider() {
            @Override
            public ProviderId id() {
                return ProviderId.of("acme");
            }

            @Override
            public Set<VerificationType> supportedTypes() {
                return Set.of(VerificationType.LIVENESS);
            }

            @Override
            public ProviderResult verify(ProviderRequest request) {
                calls.incrementAndGet();
                if (!healthy) {
                    throw new IllegalStateException("fora do ar");
                }
                return new ProviderResult(ProviderDecision.APPROVED, 99, List.of(), "tx", 0, "{}");
            }
        };
    }

    @Test
    void returnsNormalizedResultWithLatency() {
        var result = gateway(provider(true)).verify(ProviderId.of("acme"), request);

        assertThat(result.decision()).isEqualTo(ProviderDecision.APPROVED);
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void retriesThenTranslatesTechnicalFailure() {
        var gateway = gateway(provider(false));

        assertThatThrownBy(() -> gateway.verify(ProviderId.of("acme"), request))
                .isInstanceOf(ProviderUnavailableException.class)
                .extracting("errorCode")
                .isEqualTo("PROVIDER_ERROR");
        assertThat(calls).hasValue(2);
    }

    @Test
    void opensCircuitAfterRepeatedFailures() {
        var gateway = gateway(provider(false));
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> gateway.verify(ProviderId.of("acme"), request));
        }

        assertThatThrownBy(() -> gateway.verify(ProviderId.of("acme"), request))
                .isInstanceOf(ProviderUnavailableException.class)
                .extracting("errorCode")
                .isEqualTo("CIRCUIT_OPEN");
    }

    @Test
    void unknownOrUnsupportedProviderIsUnavailable() {
        var gateway = gateway(provider(true));

        assertThat(gateway.supports(ProviderId.of("acme"), VerificationType.EMAIL_OWNERSHIP))
                .isFalse();
        assertThatThrownBy(() -> gateway.verify(ProviderId.of("outro"), request))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void confirmationDoesNotRetryAndMapsMissingCredentials() {
        var gateway = gateway(new VerificationProvider() {
            @Override
            public ProviderId id() {
                return ProviderId.of("acme");
            }

            @Override
            public Set<VerificationType> supportedTypes() {
                return Set.of(VerificationType.LIVENESS);
            }

            @Override
            public ProviderResult verify(ProviderRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ProviderResult confirm(ProviderRequest request, String transactionId, String answer) {
                calls.incrementAndGet();
                throw new ProviderCredentialsMissingException(id(), "teste");
            }
        });

        assertThatThrownBy(() -> gateway.confirm(ProviderId.of("acme"), request, "tx", "123456"))
                .isInstanceOf(ProviderUnavailableException.class)
                .extracting("errorCode")
                .isEqualTo("CREDENTIALS_NOT_CONFIGURED");
        assertThat(calls).hasValue(1);
    }
}
