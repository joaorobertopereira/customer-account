package br.com.helpcsistemas.customeraccount.tenant.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.application.query.DeliveryView;
import java.time.Instant;
import java.util.List;

/** Fila durável de notificações (outbox de webhooks). */
public interface NotificationQueuePort {

    /** Idempotente por {@code eventId}. */
    void enqueue(QueuedNotification notification);

    /** Reserva (lease) notificações vencidas para esta instância; seguro com várias instâncias. */
    List<QueuedNotification> claimDue(Instant now, int limit);

    void markDelivered(String eventId, int attempts, int httpStatus, Instant at);

    void reschedule(String eventId, int attempts, Integer httpStatus, String error, Instant nextAttemptAt, Instant at);

    void markFailed(String eventId, int attempts, Integer httpStatus, String error, Instant at);

    void markDiscarded(String eventId, String reason, Instant at);

    List<DeliveryView> recent(TenantId tenant, int limit);

    record QueuedNotification(
            String eventId,
            TenantId tenantId,
            String eventType,
            String reference,
            String subjectId,
            String payload,
            int attempts,
            Instant occurredAt) {

        @Override
        public String toString() {
            return "QueuedNotification[" + eventId + "]";
        }
    }
}
