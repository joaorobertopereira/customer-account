package br.com.helpcsistemas.customeraccount.tenant.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param allowInsecureTargets permite http (somente dev/teste)
 * @param allowPrivateTargets permite destinos em rede privada/loopback (somente dev/teste)
 */
@Validated
@ConfigurationProperties("app.tenant.webhook")
public record TenantWebhookProperties(
        boolean allowInsecureTargets,
        boolean allowPrivateTargets,
        @NotNull Duration timeout,
        @Min(1) int maxAttempts,
        @Min(1) int batchSize) {}
