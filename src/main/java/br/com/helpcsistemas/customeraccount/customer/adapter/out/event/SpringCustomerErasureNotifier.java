package br.com.helpcsistemas.customeraccount.customer.adapter.out.event;

import br.com.helpcsistemas.customeraccount.customer.CustomerErasureCompleted;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerErasureNotifierPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringCustomerErasureNotifier implements CustomerErasureNotifierPort {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    SpringCustomerErasureNotifier(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public void erasureCompleted(CustomerId id) {
        publisher.publishEvent(new CustomerErasureCompleted(id.value(), clock.instant()));
    }
}
