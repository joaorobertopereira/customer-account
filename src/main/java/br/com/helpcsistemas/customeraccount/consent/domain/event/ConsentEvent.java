package br.com.helpcsistemas.customeraccount.consent.domain.event;

import br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentEvidence;
import br.com.helpcsistemas.customeraccount.shared.domain.DomainEvent;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.UUID;

public sealed interface ConsentEvent extends DomainEvent {
    UUID customerId();

    TenantId tenantId();

    String purpose();

    record ConsentGranted(
            UUID customerId,
            TenantId tenantId,
            String purpose,
            String policyVersion,
            ConsentEvidence evidence,
            Instant linkedAt,
            Instant occurredAt)
            implements ConsentEvent {}

    record ConsentRevoked(UUID customerId, TenantId tenantId, String purpose, String reason, Instant occurredAt)
            implements ConsentEvent {}
}
