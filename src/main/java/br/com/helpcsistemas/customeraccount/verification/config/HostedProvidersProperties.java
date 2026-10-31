package br.com.helpcsistemas.customeraccount.verification.config;

import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultStatus;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.verification.hosted-providers")
public record HostedProvidersProperties(Map<String, Provider> providers) {

    public HostedProvidersProperties {
        providers = providers == null ? Map.of() : Map.copyOf(providers);
    }

    public record Provider(
            boolean enabled,
            String baseUrl,
            Duration timeout,
            String authScheme,
            String authHeader,
            String createSessionPath,
            Map<String, String> requestFields,
            String sessionIdPointer,
            String urlPointer,
            String expiresAtPointer,
            Callback callback) {

        public Provider {
            timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
            authScheme = authScheme == null ? "BEARER" : authScheme;
            authHeader = authHeader == null ? "X-Api-Key" : authHeader;
            requestFields = requestFields == null ? Map.of() : Map.copyOf(requestFields);
        }
    }

    public record Callback(
            String signatureHeader,
            String timestampHeader,
            Duration tolerance,
            String eventIdPointer,
            String sessionIdPointer,
            String statusPointer,
            String scorePointer,
            String reasonCodesPointer,
            String fraudSignalsPointer,
            String occurredAtPointer,
            Map<String, HostedResultStatus> statusMap) {

        public Callback {
            tolerance = tolerance == null ? Duration.ofMinutes(5) : tolerance;
            statusMap = statusMap == null ? Map.of() : Map.copyOf(statusMap);
        }
    }
}
