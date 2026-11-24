package br.com.helpcsistemas.customeraccount.customer.adapter.in.scheduler;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerLinkIndex;
import br.com.helpcsistemas.customeraccount.customer.application.service.CustomerLinkService;
import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class CustomerLinkExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(CustomerLinkExpirationScheduler.class);
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
            // Falha num item não aborta o lote; o item volta na próxima execução.
            try {
                links.expire(request.customerId(), request.requestId());
                index.remove(request.requestId());
            } catch (ConcurrencyConflictException ex) {
                log.atDebug()
                        .addKeyValue("link.requestId", request.requestId())
                        .log("Solicitação de vínculo alterada em paralelo; nova tentativa na próxima execução");
            } catch (RuntimeException ex) {
                log.atError()
                        .addKeyValue("link.requestId", request.requestId())
                        .setCause(ex)
                        .log("Falha ao expirar solicitação de vínculo");
            }
        }
    }
}
