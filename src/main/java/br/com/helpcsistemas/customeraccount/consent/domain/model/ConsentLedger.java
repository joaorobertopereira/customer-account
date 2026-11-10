package br.com.helpcsistemas.customeraccount.consent.domain.model;

import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentGranted;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentRevoked;
import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.EventSourcedAggregate;
import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConsentLedger extends EventSourcedAggregate<ConsentEvent> {
    private final UUID customerId;
    private final TenantId tenantId;
    private final Map<String, ConsentGranted> granted = new HashMap<>();

    private ConsentLedger(UUID customerId, TenantId tenantId) {
        this.customerId = customerId;
        this.tenantId = tenantId;
    }

    public static ConsentLedger rehydrate(UUID id, TenantId tenant, List<ConsentEvent> history, long version) {
        var ledger = new ConsentLedger(id, tenant);
        ledger.replay(history, version);
        return ledger;
    }

    public void grant(Purpose purpose, ConsentEvidence evidence, Instant linkedAt, Clock clock) {
        if (purpose.legalBasis() != Purpose.LegalBasis.CONSENT) {
            throw new BusinessRuleException("CONSENT_NOT_APPLICABLE", "A finalidade utiliza outra base legal");
        }
        raise(new ConsentGranted(
                customerId, tenantId, purpose.code(), purpose.policyVersion(), evidence, linkedAt, clock.instant()));
    }

    public void revoke(String purpose, String reason, Clock clock) {
        if (reason == null || reason.isBlank() || reason.length() > 2000) {
            throw new InvalidValueException("INVALID_REVOCATION_REASON", "Informe o motivo da revogação");
        }
        if (granted.containsKey(purpose)) {
            raise(new ConsentRevoked(customerId, tenantId, purpose, reason, clock.instant()));
        }
    }

    @Override
    protected void apply(ConsentEvent event) {
        if (!customerId.equals(event.customerId()) || !tenantId.equals(event.tenantId())) {
            throw new IllegalArgumentException("Evento pertence a outro livro de consentimento");
        }
        switch (event) {
            case ConsentGranted e -> granted.put(e.purpose(), e);
            case ConsentRevoked e -> granted.remove(e.purpose());
        }
    }

    public UUID customerId() {
        return customerId;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Map<String, ConsentGranted> granted() {
        return Map.copyOf(granted);
    }
}
