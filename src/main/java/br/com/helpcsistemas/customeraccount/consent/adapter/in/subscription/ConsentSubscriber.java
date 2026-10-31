package br.com.helpcsistemas.customeraccount.consent.adapter.in.subscription;

import br.com.helpcsistemas.customeraccount.consent.adapter.codec.ConsentEventCodec;
import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentProjection;
import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentRepository;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentRevoked;
import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotification;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotifications;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class ConsentSubscriber implements EventStreamSubscriber {
    private final ConsentEventCodec codec;
    private final ConsentRepository ledgers;
    private final ConsentProjection views;
    private final CustomerDirectory customers;
    private final TenantNotifications notifications;
    private final JsonMapper json;

    ConsentSubscriber(
            ConsentEventCodec codec,
            ConsentRepository ledgers,
            ConsentProjection views,
            CustomerDirectory customers,
            TenantNotifications notifications,
            JsonMapper json) {
        this.codec = codec;
        this.ledgers = ledgers;
        this.views = views;
        this.customers = customers;
        this.notifications = notifications;
        this.json = json;
    }

    @Override
    public String subscriptionName() {
        return "consent-views-v1";
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
            if (event instanceof ConsentRevoked revoked) {
                notifications.enqueue(new TenantNotification(
                        revoked.tenantId(),
                        stored.stream() + ":" + stored.revision(),
                        "consent.revoked",
                        revoked.purpose(),
                        revoked.customerId().toString(),
                        json.writeValueAsString(Map.of(
                                "eventType",
                                "consent.revoked",
                                "customerId",
                                revoked.customerId().toString(),
                                "purpose",
                                revoked.purpose(),
                                "occurredAt",
                                revoked.occurredAt())),
                        revoked.occurredAt()));
            }
        } catch (PersonalDataErasedException ex) {
            LoggerFactory.getLogger(getClass())
                    .atInfo()
                    .addKeyValue("event.type", stored.type())
                    .log("Consentimento de titular eliminado ignorado");
        }
    }
}
