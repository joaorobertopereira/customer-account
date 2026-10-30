package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Índices operacionais para reuso de sessões ativas e correlação de callbacks. */
public interface HostedSessionRegistryPort {

    Duration LEASE = Duration.ofMinutes(2);

    Optional<HostedSessionReference> findActive(TenantId tenant, UUID customerId, VerificationType type, Instant now);

    boolean claim(
            TenantId tenant,
            UUID customerId,
            VerificationType type,
            VerificationId verificationId,
            Instant leaseUntil,
            Instant now);

    void activate(HostedSessionReference session);

    Optional<HostedSessionReference> findByProviderSession(ProviderId provider, String sessionId);

    void release(VerificationId verificationId);

    void deleteByCustomer(UUID customerId);

    record HostedSessionReference(
            TenantId tenant,
            UUID customerId,
            VerificationType type,
            VerificationId verificationId,
            ProviderId provider,
            String sessionId,
            Instant expiresAt,
            boolean ready) {}
}
