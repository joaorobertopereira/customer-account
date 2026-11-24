package br.com.helpcsistemas.customeraccount.verification.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.eventsourcing.AggregateSnapshots;
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
    private final AggregateSnapshots snapshots;

    EventStoreVerificationPolicyRepository(
            EventStore eventStore, VerificationEventCodec codec, AggregateSnapshots snapshots) {
        this.snapshots = snapshots;
        this.eventStore = eventStore;
        this.codec = codec;
    }

    @Override
    public TenantVerificationPolicy load(TenantId tenant) {
        return snapshots
                .load(
                        codec.policyStreamOf(tenant),
                        TenantVerificationPolicy.class,
                        null,
                        codec::decodePolicy,
                        stored -> TenantVerificationPolicy.rehydrate(
                                tenant,
                                stored.stream().map(codec::decodePolicy).toList(),
                                stored.getLast().revision()))
                .orElseGet(() -> TenantVerificationPolicy.empty(tenant));
    }

    @Override
    public void save(TenantVerificationPolicy policy) {
        if (!policy.hasPendingEvents()) {
            return;
        }
        var stream = codec.policyStreamOf(policy.tenantId());
        var previous = policy.version();
        var events = policy.pendingEvents().stream().map(codec::encodePolicy).toList();
        try {
            long revision = eventStore.append(stream, ExpectedRevision.of(previous), events);
            policy.markCommitted(revision);
        } catch (StreamRevisionConflictException ex) {
            throw new ConcurrencyConflictException(
                    "VERIFICATION_POLICY_CONCURRENT_MODIFICATION",
                    "A política foi alterada por outro administrador. Consulte novamente e repita a alteração.");
        }
        snapshots.maybeSnapshot(stream, policy, previous, policy.version(), null, null);
    }

    @Override
    public List<VerificationRuleConfigured> history(TenantId tenant) {
        return eventStore.readStream(codec.policyStreamOf(tenant)).stream()
                .map(codec::decodePolicy)
                .map(VerificationRuleConfigured.class::cast)
                .toList();
    }
}
