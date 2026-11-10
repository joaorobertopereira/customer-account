package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SubjectLinkChallengePort {

    void save(Challenge challenge);

    Optional<Challenge> findLatestForSubject(String subject, TenantId tenant, Instant now);

    Optional<Challenge> find(String id, String subject, TenantId tenant, Instant now);

    Optional<Challenge> consume(String id, String subject, TenantId tenant, String codeHash, Instant now);

    void recordFailedAttempt(String id, String subject, TenantId tenant, Instant now);

    void delete(String id);

    record Challenge(
            String id,
            String subject,
            TenantId tenant,
            UUID customerId,
            String codeHash,
            Instant createdAt,
            Instant expiresAt,
            int attempts) {}
}
