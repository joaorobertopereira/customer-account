package br.com.helpcsistemas.customeraccount.customer.adapter.in.subscription;

import br.com.helpcsistemas.customeraccount.customer.CustomerTenantUnlinked;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerEventCodec;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerLinkIndex;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkApproved;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkRejected;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkRequested;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerUnlinkedFromTenant;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotification;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotifications;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class CustomerLinkSubscriber implements EventStreamSubscriber {
    private final CustomerEventCodec codec;
    private final CustomerLinkIndex index;
    private final TenantNotifications notifications;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper json;

    CustomerLinkSubscriber(
            CustomerEventCodec codec,
            CustomerLinkIndex index,
            TenantNotifications notifications,
            ApplicationEventPublisher publisher,
            JsonMapper json) {
        this.codec = codec;
        this.index = index;
        this.notifications = notifications;
        this.publisher = publisher;
        this.json = json;
    }

    @Override
    public String subscriptionName() {
        return "customer-links-v1";
    }

    @Override
    public String streamPrefix() {
        return CustomerEventCodec.STREAM_PREFIX;
    }

    @Override
    public void onEvent(StoredEvent stored) {
        if (!java.util.Set.of(
                        "CustomerLinkRequested.v1",
                        "CustomerLinkApproved.v1",
                        "CustomerLinkRejected.v1",
                        "CustomerUnlinkedFromTenant.v1")
                .contains(stored.type())) {
            return;
        }
        try {
            switch (codec.decode(stored)) {
                case CustomerLinkRequested e -> index.pending(e.customerId().value(), e.requestId(), e.expiresAt());
                case CustomerLinkRejected e -> index.remove(e.requestId());
                case CustomerLinkApproved e -> {
                    index.remove(e.requestId());
                    notify(stored, e.tenantId(), e.customerId().toString(), "customer.link_approved");
                }
                case CustomerUnlinkedFromTenant e -> {
                    publisher.publishEvent(
                            new CustomerTenantUnlinked(e.customerId().value(), e.tenantId(), e.occurredAt()));
                    notify(stored, e.tenantId(), e.customerId().toString(), "customer.unlinked");
                }
                default -> throw new IllegalStateException("Evento de vínculo inesperado");
            }
        } catch (PersonalDataErasedException ex) {
            LoggerFactory.getLogger(getClass())
                    .atInfo()
                    .addKeyValue("event.type", stored.type())
                    .log("Vínculo de titular eliminado ignorado");
        }
    }

    private void notify(
            StoredEvent stored,
            br.com.helpcsistemas.customeraccount.shared.domain.TenantId tenant,
            String customer,
            String type) {
        notifications.enqueue(new TenantNotification(
                tenant,
                stored.stream() + ":" + stored.revision(),
                type,
                customer,
                customer,
                json.writeValueAsString(java.util.Map.of("eventType", type, "customerId", customer)),
                codec.decode(stored).occurredAt()));
    }
}
