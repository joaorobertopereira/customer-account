package br.com.helpcsistemas.customeraccount.tenant.adapter.out.event;

import br.com.helpcsistemas.customeraccount.tenant.TenantNotificationDelivered;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotificationFailed;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.TenantEventsPublisherPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringTenantEventsPublisher implements TenantEventsPublisherPort {

    private final ApplicationEventPublisher publisher;

    SpringTenantEventsPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void delivered(TenantNotificationDelivered event) {
        publisher.publishEvent(event);
    }

    @Override
    public void failed(TenantNotificationFailed event) {
        publisher.publishEvent(event);
    }
}
