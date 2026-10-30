package br.com.helpcsistemas.customeraccount.privacy.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param keyProvider {@code vault} em qualquer ambiente real; {@code local} apenas para desenvolvimento/testes
 * @param blindIndexKey chave HMAC em base64 (mín. 32 bytes), vinda de secret manager
 * @param localMasterKey KEK local em base64 (32 bytes), usada só com {@code key-provider=local}
 */
@Validated
@ConfigurationProperties("app.privacy")
public record PrivacyProperties(
        @NotNull KeyProvider keyProvider,
        @NotBlank String blindIndexKey,
        String localMasterKey,
        @NotNull Duration dataKeyCacheTtl,
        @Positive long dataKeyCacheMaxSize,
        @Valid Vault vault) {

    public enum KeyProvider {
        VAULT,
        LOCAL
    }

    public record Vault(
            String uri, String token, String tokenFile, String transitMount, String keyName, Duration timeout) {}
}
