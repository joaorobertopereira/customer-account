package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSession;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderDescriptor;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderUnavailableException;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProviderGateway;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Executa a chamada ao provedor com circuit breaker e retry próprios por provedor (config {@code providers}),
 * métricas/spans por provedor e tradução de qualquer falha técnica em {@link ProviderUnavailableException}.
 */
@Component
class ResilientProviderGateway implements VerificationProviderGateway {

    static final String RESILIENCE_CONFIG = "providers";

    private final Map<ProviderId, VerificationProvider> providers;
    private final CircuitBreakerRegistry circuitBreakers;
    private final RetryRegistry retries;
    private final ObservationRegistry observations;

    ResilientProviderGateway(
            List<VerificationProvider> providers,
            CircuitBreakerRegistry circuitBreakers,
            RetryRegistry retries,
            ObservationRegistry observations) {
        this.providers = providers.stream().collect(Collectors.toMap(VerificationProvider::id, Function.identity()));
        this.circuitBreakers = circuitBreakers;
        this.retries = retries;
        this.observations = observations;
    }

    @Override
    public ProviderResult verify(ProviderId providerId, ProviderRequest request) {
        var provider = resolve(providerId, request);
        var name = "provider-" + providerId.value();
        var circuitBreaker = circuitBreakers.circuitBreaker(name, RESILIENCE_CONFIG);
        var retry = retries.retry(name, RESILIENCE_CONFIG);
        var call = Retry.decorateSupplier(
                retry, CircuitBreaker.decorateSupplier(circuitBreaker, () -> provider.verify(request)));
        return execute("verification.provider.call", providerId, request, call);
    }

    /** Sem retry: reenviar a checagem poderia consumir tentativas do titular no provedor. */
    @Override
    public ProviderResult confirm(ProviderId providerId, ProviderRequest request, String transactionId, String answer) {
        var provider = resolve(providerId, request);
        var circuitBreaker = circuitBreakers.circuitBreaker("provider-" + providerId.value(), RESILIENCE_CONFIG);
        Supplier<ProviderResult> call =
                CircuitBreaker.decorateSupplier(circuitBreaker, () -> provider.confirm(request, transactionId, answer));
        return execute("verification.provider.confirm", providerId, request, call);
    }

    @Override
    public HostedSession startHosted(ProviderId providerId, ProviderRequest request, URI returnUrl) {
        var provider = resolve(providerId, request);
        if (!(provider instanceof HostedVerificationProvider hosted)) {
            throw new ProviderUnavailableException("PROVIDER_NOT_AVAILABLE", 0, null);
        }
        var name = "provider-" + providerId.value();
        var circuitBreaker = circuitBreakers.circuitBreaker(name, RESILIENCE_CONFIG);
        var retry = retries.retry(name, RESILIENCE_CONFIG);
        var call = Retry.decorateSupplier(
                retry, CircuitBreaker.decorateSupplier(circuitBreaker, () -> hosted.start(request, returnUrl)));
        return executeHosted("verification.provider.hosted.start", providerId, request, call);
    }

    private VerificationProvider resolve(ProviderId providerId, ProviderRequest request) {
        var provider = providers.get(providerId);
        if (provider == null || !provider.supportedTypes().contains(request.type())) {
            throw new ProviderUnavailableException("PROVIDER_NOT_AVAILABLE", 0, null);
        }
        return provider;
    }

    private ProviderResult execute(
            String observationName, ProviderId providerId, ProviderRequest request, Supplier<ProviderResult> call) {
        long start = System.nanoTime();
        try {
            var result = Observation.createNotStarted(observationName, observations)
                    .lowCardinalityKeyValue("provider", providerId.value())
                    .lowCardinalityKeyValue("verification.type", request.type().name())
                    .observe(call::get);
            return result.withLatency(elapsedMs(start));
        } catch (CallNotPermittedException ex) {
            throw new ProviderUnavailableException("CIRCUIT_OPEN", elapsedMs(start), ex);
        } catch (RuntimeException ex) {
            throw new ProviderUnavailableException(errorCode(ex), elapsedMs(start), ex);
        }
    }

    private HostedSession executeHosted(
            String observationName, ProviderId providerId, ProviderRequest request, Supplier<HostedSession> call) {
        long start = System.nanoTime();
        try {
            return Observation.createNotStarted(observationName, observations)
                    .lowCardinalityKeyValue("provider", providerId.value())
                    .lowCardinalityKeyValue("verification.type", request.type().name())
                    .observe(call::get);
        } catch (CallNotPermittedException ex) {
            throw new ProviderUnavailableException("CIRCUIT_OPEN", elapsedMs(start), ex);
        } catch (RuntimeException ex) {
            throw new ProviderUnavailableException(errorCode(ex), elapsedMs(start), ex);
        }
    }

    @Override
    public boolean supports(ProviderId provider, VerificationType type) {
        var candidate = providers.get(provider);
        return candidate != null && candidate.supportedTypes().contains(type);
    }

    @Override
    public List<ProviderDescriptor> providers() {
        return providers.values().stream()
                .map(p -> new ProviderDescriptor(p.id(), p.supportedTypes()))
                .sorted(Comparator.comparing(d -> d.id().value()))
                .toList();
    }

    private static String errorCode(Throwable ex) {
        for (var cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException) {
                return "TIMEOUT";
            }
            if (cause instanceof RestClientResponseException http) {
                return "HTTP_" + http.getStatusCode().value();
            }
            if (cause instanceof ProviderCredentialsMissingException) {
                return "CREDENTIALS_NOT_CONFIGURED";
            }
        }
        return "PROVIDER_ERROR";
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
