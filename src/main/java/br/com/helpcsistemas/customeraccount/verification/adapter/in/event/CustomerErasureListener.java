package br.com.helpcsistemas.customeraccount.verification.adapter.in.event;

import br.com.helpcsistemas.customeraccount.customer.CustomerErasureCompleted;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ProjectVerificationEventUseCase;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class CustomerErasureListener {

    private final ProjectVerificationEventUseCase projection;

    CustomerErasureListener(ProjectVerificationEventUseCase projection) {
        this.projection = projection;
    }

    @EventListener
    public void on(CustomerErasureCompleted event) {
        projection.eraseCustomerData(event.customerId());
    }
}
