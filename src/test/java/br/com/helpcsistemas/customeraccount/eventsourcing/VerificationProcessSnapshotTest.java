package br.com.helpcsistemas.customeraccount.eventsourcing;

import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.FALLBACK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.PRIMARY;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.TENANT;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.result;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationProcessSnapshotTest {

    private final Map<String, Snapshot> saved = new HashMap<>();
    private final AggregateSnapshots snapshots = new AggregateSnapshots(
            new SnapshotStore() {
                public Optional<Snapshot> load(String stream) {
                    return Optional.ofNullable(saved.get(stream));
                }

                public void save(Snapshot snapshot) {
                    saved.put(snapshot.stream(), snapshot);
                }
            },
            new EventStore() {
                public long append(String s, ExpectedRevision e, List<NewEvent> n) {
                    throw new UnsupportedOperationException();
                }

                public List<StoredEvent> readStream(String s) {
                    return List.of();
                }

                public List<StoredEvent> readStream(String s, long from) {
                    return List.of();
                }

                public Optional<StoredEvent> readLastEvent(String s) {
                    return Optional.empty();
                }
            },
            CLOCK);

    @Test
    void processWithFailoverSurvivesSnapshotRoundTrip() {
        var process = VerificationProcess.request(
                VerificationId.newId(), TENANT, UUID.randomUUID(), rule(PRIMARY, FALLBACK), 3, "tester", CLOCK);
        process.submit(CLOCK);
        process.providerFailed("TIMEOUT", 1200, CLOCK);
        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.APPROVED, 90), CLOCK);
        process.markCommitted(7);

        snapshots.maybeSnapshot("verification-x", process, -1, 9, "subject", plain -> plain);
        var restored = snapshots
                .load("verification-x", VerificationProcess.class, (subject, data) -> data, s -> null, s -> null)
                .orElseThrow();

        assertThat(restored.id()).isEqualTo(process.id());
        assertThat(restored.tenantId()).isEqualTo(process.tenantId());
        assertThat(restored.customerId()).isEqualTo(process.customerId());
        assertThat(restored.rule()).isEqualTo(process.rule());
        assertThat(restored.status()).isEqualTo(process.status());
        assertThat(restored.outcome()).isEqualTo(process.outcome());
        assertThat(restored.score()).isEqualTo(process.score());
        assertThat(restored.failedOver()).isTrue();
        assertThat(restored.failures()).isEqualTo(process.failures());
        assertThat(restored.requestedAt()).isEqualTo(process.requestedAt());
        assertThat(restored.version()).isEqualTo(9);
    }
}
