package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Liga/desliga cada integração. Credenciais NÃO ficam aqui: vêm do cofre ({@link ProviderCredentialsResolver}). */
@Validated
@ConfigurationProperties("app.verification.providers")
public record VerificationProvidersProperties(
        @NotNull @Valid OwnEmailOtp ownEmailOtp,
        @NotNull @Valid Http twilioVerify,
        @NotNull @Valid Http twilioLookup,
        @NotNull @Valid Http zerobounce) {

    public record OwnEmailOtp(boolean enabled, String mailFrom) {}

    public record Http(boolean enabled, String baseUrl, Duration timeout) {}
}
