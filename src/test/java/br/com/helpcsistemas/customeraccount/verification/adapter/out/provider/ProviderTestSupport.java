package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationSubject;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ProviderTestSupport {

    public static final TenantId TENANT = TenantId.of("tenant-a");

    private ProviderTestSupport() {}

    /** Cofre em memória: chaves no formato {@code providers/<provedor>/default}. */
    public static ProviderCredentialsResolver credentials(Map<String, Map<String, String>> secrets) {
        return new ProviderCredentialsResolver(path -> Optional.ofNullable(secrets.get(path)));
    }

    public static ProviderRequest request(VerificationType type) {
        return new ProviderRequest(
                TENANT,
                VerificationId.newId(),
                type,
                new VerificationSubject(
                        UUID.randomUUID(),
                        "52998224725",
                        "Maria Souza",
                        LocalDate.of(1990, 1, 1),
                        "maria@example.com",
                        "+5511912345678"));
    }
}
