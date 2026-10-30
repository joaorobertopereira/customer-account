package br.com.helpcsistemas.customeraccount.verification.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationEventCodec;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationPolicyRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import br.com.helpcsistemas.customeraccount.verification.domain.model.TenantVerificationPolicy;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class EventStoreVerificationPolicyRepository implements VerificationPolicyRepositoryPort {

    private final EventStore eventStore;
    private final VerificationEventCodec codec;

    EventStoreVerificationPolicyRepository(EventStore eventStore, VerificationEventCodec codec) {
        this.eventStore = eventStore;
        this.codec = codec;
    }

    @Override
    public TenantVerificationPolicy load(TenantId tenant) {
        var stored = eventStore.readStream(codec.policyStreamOf(tenant));
        if (stored.isEmpty()) {
            return TenantVerificationPolicy.empty(tenant);
        }
        var history = stored.stream().map(codec::decodePolicy).toList();
        return TenantVerificationPolicy.rehydrate(
                tenant, history, stored.getLast().revision());
    }

    @Override
    public void save(TenantVerificationPolicy policy) {
        if (!policy.hasPendingEvents()) {
            return;
        }
        var events = policy.pendingEvents().stream().map(codec::encodePolicy).toList();
        try {
            long revision = eventStore.append(
                    codec.policyStreamOf(policy.tenantId()), ExpectedRevision.of(policy.version()), events);
            policy.markCommitted(revision);
        } catch (StreamRevisionConflictException ex) {
            throw new ConcurrencyConflictException(
                    "VERIFICATION_POLICY_CONCURRENT_MODIFICATION",
                    "A política foi alterada por outro administrador. Consulte novamente e repita a alteração.");
        }
    }

    @Override
    public List<VerificationRuleConfigured> history(TenantId tenant) {
        return eventStore.readStream(codec.policyStreamOf(tenant)).stream()
                .map(codec::decodePolicy)
                .map(VerificationRuleConfigured.class::cast)
                .toList();
    }
}
