package br.com.helpcsistemas.customeraccount.consent.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.consent.adapter.codec.ConsentEventCodec;
import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentRepository;
import br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentLedger;
import br.com.helpcsistemas.customeraccount.eventsourcing.AggregateSnapshots;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EventStoreConsentRepository implements ConsentRepository {
    private final EventStore events;
    private final ConsentEventCodec codec;
    private final AggregateSnapshots snapshots;
    private final PersonalDataCipher cipher;

    EventStoreConsentRepository(
            EventStore events, ConsentEventCodec codec, AggregateSnapshots snapshots, PersonalDataCipher cipher) {
        this.events = events;
        this.codec = codec;
        this.snapshots = snapshots;
        this.cipher = cipher;
    }

    private static String stream(UUID id, TenantId tenant) {
        return "consent-" + id + "-" + tenant.value();
    }

    @Override
    public ConsentLedger load(UUID id, TenantId tenant) {
        return snapshots
                .load(
                        stream(id, tenant),
                        ConsentLedger.class,
                        cipher::decrypt,
                        codec::decode,
                        history -> ConsentLedger.rehydrate(
                                id,
                                tenant,
                                history.stream().map(codec::decode).toList(),
                                history.getLast().revision()))
                .orElseGet(() -> ConsentLedger.rehydrate(id, tenant, List.of(), -1));
    }

    @Override
    public void save(ConsentLedger ledger) {
        if (!ledger.hasPendingEvents()) {
            return;
        }
        var stream = stream(ledger.customerId(), ledger.tenantId());
        var previous = ledger.version();
        try {
            var revision = events.append(
                    stream,
                    ExpectedRevision.of(previous),
                    ledger.pendingEvents().stream().map(codec::encode).toList());
            ledger.markCommitted(revision);
        } catch (StreamRevisionConflictException ex) {
            throw new ConcurrencyConflictException(
                    "CONSENT_CONCURRENT_MODIFICATION", "Consentimento alterado concorrentemente");
        }
        var subject = ledger.customerId().toString();
        snapshots.maybeSnapshot(
                stream, ledger, previous, ledger.version(), subject, plain -> cipher.encrypt(subject, plain));
    }
}
