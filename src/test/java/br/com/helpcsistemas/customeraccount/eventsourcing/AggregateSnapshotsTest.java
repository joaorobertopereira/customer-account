package br.com.helpcsistemas.customeraccount.eventsourcing;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_A;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentLedger;
import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerEvent;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.TenantVerificationPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AggregateSnapshotsTest {

    private final Map<String, Snapshot> saved = new HashMap<>();
    private final Map<String, List<StoredEvent>> streams = new HashMap<>();
    private final Map<UUID, CustomerEvent> decoded = new HashMap<>();

    private final SnapshotStore store = new SnapshotStore() {
        @Override
        public Optional<Snapshot> load(String stream) {
            return Optional.ofNullable(saved.get(stream));
        }

        @Override
        public void save(Snapshot snapshot) {
            saved.put(snapshot.stream(), snapshot);
        }
    };

    private final EventStore events = new EventStore() {
        @Override
        public long append(String stream, ExpectedRevision expected, List<NewEvent> events) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StoredEvent> readStream(String stream) {
            return streams.getOrDefault(stream, List.of());
        }

        @Override
        public List<StoredEvent> readStream(String stream, long fromRevision) {
            return readStream(stream).stream()
                    .filter(e -> e.revision() >= fromRevision)
                    .toList();
        }

        @Override
        public Optional<StoredEvent> readLastEvent(String stream) {
            return Optional.empty();
        }
    };

    private final AggregateSnapshots snapshots = new AggregateSnapshots(store, events, CLOCK);

    private StoredEvent stored(String stream, long revision, CustomerEvent event) {
        var id = UUID.randomUUID();
        decoded.put(id, event);
        return new StoredEvent(
                stream, revision, id, event.getClass().getSimpleName(), new byte[0], Map.of(), Instant.now());
    }

    @Test
    void snapshotsOnlyWhenCrossingATenEventBoundary() {
        var customer = CustomerFixtures.persistedCustomer(CustomerId.newId());

        snapshots.maybeSnapshot("s", customer, -1, 8, null, null);
        assertThat(saved).isEmpty();

        snapshots.maybeSnapshot("s", customer, 8, 9, null, null);
        assertThat(saved).containsKey("s");
        assertThat(saved.get("s").revision()).isEqualTo(9);

        saved.clear();
        snapshots.maybeSnapshot("s", customer, 9, 10, null, null);
        assertThat(saved).isEmpty();

        snapshots.maybeSnapshot("s", customer, 15, 25, null, null);
        assertThat(saved).containsKey("s");
    }

    @Test
    void customerRestoredFromSnapshotPlusTailEqualsFullReplay() {
        var id = CustomerId.newId();
        var customer = CustomerFixtures.persistedCustomer(id);
        customer.changeEmail(
                TENANT_A,
                new br.com.helpcsistemas.customeraccount.customer.domain.model.Email("novo@example.com"),
                CLOCK);
        var history = new ArrayList<>(customer.pendingEvents());
        customer.markCommitted(history.size() - 1);
        var stream = "customer-" + id;
        snapshots.maybeSnapshot(stream, customer, -1, history.size() - 1 + 9, null, null);
        var snapRevision = saved.get(stream).revision();

        // eventos posteriores ao snapshot (cauda)
        customer.requestLink(TENANT_B, customer.personalData(), customer.email(), customer.mobilePhone(), null, CLOCK);
        var tailEvents = customer.pendingEvents();
        var all = new ArrayList<StoredEvent>();
        for (int i = 0; i <= snapRevision; i++) {
            all.add(stored(stream, i, history.get(Math.min(i, history.size() - 1))));
        }
        tailEvents.forEach(e -> all.add(stored(stream, all.size(), e)));
        streams.put(stream, all);

        var restored = snapshots
                .load(stream, Customer.class, null, s -> decoded.get(s.eventId()), s -> {
                    throw new AssertionError("não deveria cair no replay completo");
                })
                .orElseThrow();

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.email()).isEqualTo(customer.email());
        assertThat(restored.tenants()).isEqualTo(customer.tenants());
        assertThat(restored.linkedAt(TENANT_A)).isEqualTo(customer.linkedAt(TENANT_A));
        assertThat(restored.linkRequests()).isEqualTo(customer.linkRequests());
        assertThat(restored.version()).isEqualTo(all.getLast().revision());
        assertThat(restored.hasPendingEvents()).isFalse();
    }

    @Test
    void consentLedgerAndPolicyRoundTrip() {
        var customerId = UUID.randomUUID();
        var ledger = ConsentLedger.rehydrate(customerId, TENANT_A, List.of(), -1);
        snapshots.maybeSnapshot("consent", ledger, -1, 9, null, null);
        var ledgerBack = snapshots
                .load("consent", ConsentLedger.class, null, s -> null, s -> null)
                .orElseThrow();
        assertThat(ledgerBack.customerId()).isEqualTo(customerId);
        assertThat(ledgerBack.tenantId()).isEqualTo(TENANT_A);
        assertThat(ledgerBack.version()).isEqualTo(9);

        var policy = TenantVerificationPolicy.empty(TENANT_B);
        snapshots.maybeSnapshot("policy", policy, -1, 9, null, null);
        var policyBack = snapshots
                .load("policy", TenantVerificationPolicy.class, null, s -> null, s -> null)
                .orElseThrow();
        assertThat(policyBack.tenantId()).isEqualTo(TENANT_B);
    }
}
