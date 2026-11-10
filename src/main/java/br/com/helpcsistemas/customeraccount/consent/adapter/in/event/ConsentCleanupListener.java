package br.com.helpcsistemas.customeraccount.consent.adapter.in.event;

import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentProjection;
import br.com.helpcsistemas.customeraccount.customer.CustomerErasureCompleted;
import br.com.helpcsistemas.customeraccount.customer.CustomerTenantUnlinked;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class ConsentCleanupListener {
    private final ConsentProjection views;
    private final br.com.helpcsistemas.customeraccount.customer.CustomerDirectory customers;

    ConsentCleanupListener(
            ConsentProjection views, br.com.helpcsistemas.customeraccount.customer.CustomerDirectory customers) {
        this.views = views;
        this.customers = customers;
    }

    @EventListener
    public void on(CustomerTenantUnlinked event) {
        views.removeStale(
                event.customerId(),
                event.tenantId(),
                customers.linkedAt(event.tenantId(), event.customerId()).orElse(null));
    }

    @EventListener
    public void on(CustomerErasureCompleted event) {
        views.erase(event.customerId());
    }
}
