package br.com.helpcsistemas.customeraccount.verification.adapter.in.event;

import br.com.helpcsistemas.customeraccount.customer.CustomerTenantUnlinked;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationViewRepositoryPort;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class CustomerUnlinkListener {
    private final VerificationViewRepositoryPort views;

    CustomerUnlinkListener(VerificationViewRepositoryPort views) {
        this.views = views;
    }

    @EventListener
    public void on(CustomerTenantUnlinked event) {
        views.deleteByTenantAndCustomer(event.tenantId(), event.customerId(), event.occurredAt());
    }
}
