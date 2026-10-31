package br.com.helpcsistemas.customeraccount.customer.adapter.in.scheduler;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerLinkIndex;
import br.com.helpcsistemas.customeraccount.customer.application.service.CustomerLinkService;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CustomerLinkExpirationScheduler {
    private final CustomerLinkIndex index;
    private final CustomerLinkService links;
    private final Clock clock;

    CustomerLinkExpirationScheduler(CustomerLinkIndex index, CustomerLinkService links, Clock clock) {
        this.index = index;
        this.links = links;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.customer.link-expiration-delay-ms:60000}")
    public void expire() {
        for (var request : index.expired(clock.instant())) {
            links.expire(request.customerId(), request.requestId());
            index.remove(request.requestId());
        }
    }
}
