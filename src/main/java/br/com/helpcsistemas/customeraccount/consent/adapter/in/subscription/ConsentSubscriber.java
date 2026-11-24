package br.com.helpcsistemas.customeraccount.consent.adapter.in.subscription;

import br.com.helpcsistemas.customeraccount.consent.adapter.codec.ConsentEventCodec;
import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentProjection;
import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentRepository;
import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Projeção pura de consentimentos; o aviso ao tenant fica em {@link ConsentRevocationNotifier}. */
@Component
class ConsentSubscriber implements EventStreamSubscriber {

    static final String NAME = "consent-views-v1";

    private final ConsentEventCodec codec;
    private final ConsentRepository ledgers;
    private final ConsentProjection views;
    private final CustomerDirectory customers;

    ConsentSubscriber(
            ConsentEventCodec codec, ConsentRepository ledgers, ConsentProjection views, CustomerDirectory customers) {
        this.codec = codec;
        this.ledgers = ledgers;
        this.views = views;
        this.customers = customers;
    }

    @Override
    public String subscriptionName() {
        return NAME;
    }

    @Override
    public boolean rebuildable() {
        return true;
    }

    @Override
    public void resetProjection() {
        views.clear();
    }

    @Override
    public String streamPrefix() {
        return "consent-";
    }

    @Override
    public void onEvent(StoredEvent stored) {
        try {
            var event = codec.decode(stored);
            var linkedAt = customers.linkedAt(event.tenantId(), event.customerId());
            if (linkedAt.isPresent()) {
                var ledger = ledgers.load(event.customerId(), event.tenantId());
                views.save(
                        event.customerId(),
                        event.tenantId(),
                        ledger.version(),
                        linkedAt.get(),
                        ledger.granted().values().stream()
                                .filter(e -> e.linkedAt().equals(linkedAt.get()))
                                .toList());
            } else {
                views.remove(event.customerId(), event.tenantId());
            }
        } catch (PersonalDataErasedException ex) {
            LoggerFactory.getLogger(getClass())
                    .atInfo()
                    .addKeyValue("event.type", stored.type())
                    .log("Consentimento de titular eliminado ignorado");
        }
    }
}
