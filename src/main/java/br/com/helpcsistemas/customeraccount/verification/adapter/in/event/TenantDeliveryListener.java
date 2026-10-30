package br.com.helpcsistemas.customeraccount.verification.adapter.in.event;

import br.com.helpcsistemas.customeraccount.tenant.TenantNotificationDelivered;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotificationFailed;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.VerificationLifecycleUseCase;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Registra no processo de validação o desfecho da entrega ao webhook do tenant. */
@Component
class TenantDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(TenantDeliveryListener.class);
    static final String EVENT_TYPE = "verification.completed";

    private final VerificationLifecycleUseCase lifecycle;

    TenantDeliveryListener(VerificationLifecycleUseCase lifecycle) {
        this.lifecycle = lifecycle;
    }

    @EventListener
    public void on(TenantNotificationDelivered event) {
        if (EVENT_TYPE.equals(event.eventType())) {
            safely(() -> lifecycle.recordDelivered(
                    VerificationId.of(event.reference()), event.httpStatus(), event.attempts()));
        }
    }

    @EventListener
    public void on(TenantNotificationFailed event) {
        if (EVENT_TYPE.equals(event.eventType())) {
            safely(() -> lifecycle.recordDeliveryFailed(
                    VerificationId.of(event.reference()), event.attempts(), event.lastHttpStatus()));
        }
    }

    // O registro é auxiliar: falhar aqui não pode desfazer a entrega já realizada.
    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível registrar o desfecho da entrega no processo de validação");
        }
    }
}
