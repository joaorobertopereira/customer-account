package br.com.helpcsistemas.customeraccount.tenant.application.port.out;

import br.com.helpcsistemas.customeraccount.tenant.TenantNotificationDelivered;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotificationFailed;

public interface TenantEventsPublisherPort {

    void delivered(TenantNotificationDelivered event);

    void failed(TenantNotificationFailed event);
}
