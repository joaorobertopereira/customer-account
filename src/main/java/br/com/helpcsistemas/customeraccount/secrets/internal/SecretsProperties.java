package br.com.helpcsistemas.customeraccount.secrets.internal;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param provider {@code vault} (padrão) ou {@code local} (somente dev/teste)
 * @param local segredos por caminho, ex.: {@code app.secrets.local[providers/zerobounce/default].api_key}
 */
@Validated
@ConfigurationProperties("app.secrets")
public record SecretsProperties(
        @NotNull Provider provider, @NotNull Duration cacheTtl, Vault vault, Map<String, Map<String, String>> local) {

    public SecretsProperties {
        local = local == null ? Map.of() : Map.copyOf(local);
    }

    public enum Provider {
        VAULT,
        LOCAL
    }

    public record Vault(
            String uri, String token, String tokenFile, String kvMount, String basePath, Duration timeout) {}
}
