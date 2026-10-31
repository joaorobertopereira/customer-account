package br.com.helpcsistemas.customeraccount.consent.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.consent.adapter.codec.ConsentEventCodec;
import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentRepository;
import br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentLedger;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EventStoreConsentRepository implements ConsentRepository {
    private final EventStore events;
    private final ConsentEventCodec codec;

    EventStoreConsentRepository(EventStore events, ConsentEventCodec codec) {
        this.events = events;
        this.codec = codec;
    }

    private static String stream(UUID id, TenantId tenant) {
        return "consent-" + id + "-" + tenant.value();
    }

    @Override
    public ConsentLedger load(UUID id, TenantId tenant) {
        var history = events.readStream(stream(id, tenant));
        return ConsentLedger.rehydrate(
                id,
                tenant,
                history.stream().map(codec::decode).toList(),
                history.isEmpty() ? -1 : history.getLast().revision());
    }

    @Override
    public void save(ConsentLedger ledger) {
        if (!ledger.hasPendingEvents()) {
            return;
        }
        try {
            var revision = events.append(
                    stream(ledger.customerId(), ledger.tenantId()),
                    ExpectedRevision.of(ledger.version()),
                    ledger.pendingEvents().stream().map(codec::encode).toList());
            ledger.markCommitted(revision);
        } catch (StreamRevisionConflictException ex) {
            throw new ConcurrencyConflictException(
                    "CONSENT_CONCURRENT_MODIFICATION", "Consentimento alterado concorrentemente");
        }
    }
}
