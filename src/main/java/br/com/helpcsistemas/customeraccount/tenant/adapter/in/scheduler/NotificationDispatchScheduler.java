package br.com.helpcsistemas.customeraccount.tenant.adapter.in.scheduler;

import br.com.helpcsistemas.customeraccount.tenant.application.port.in.DispatchNotificationsUseCase;
import br.com.helpcsistemas.customeraccount.tenant.config.TenantWebhookProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Entrega as notificações vencidas. Várias instâncias podem rodar: cada item é reservado (lease) por uma delas. */
@Component
class NotificationDispatchScheduler {

    private final DispatchNotificationsUseCase dispatcher;
    private final int batchSize;

    NotificationDispatchScheduler(DispatchNotificationsUseCase dispatcher, TenantWebhookProperties properties) {
        this.dispatcher = dispatcher;
        this.batchSize = properties.batchSize();
    }

    @Scheduled(fixedDelayString = "${app.tenant.webhook.dispatch-interval:5s}")
    public void dispatch() {
        dispatcher.dispatchDue(batchSize);
    }
}
