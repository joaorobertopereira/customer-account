package br.com.helpcsistemas.customeraccount.tenant.application.service;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotification;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotificationDelivered;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotificationFailed;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotifications;
import br.com.helpcsistemas.customeraccount.tenant.application.port.in.DispatchNotificationsUseCase;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.NotificationQueuePort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.NotificationQueuePort.QueuedNotification;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.TenantEventsPublisherPort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookEndpointRepositoryPort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookSenderPort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookSenderPort.WebhookSendException;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookTargetGuardPort;
import br.com.helpcsistemas.customeraccount.tenant.domain.model.DeliveryPolicy;
import br.com.helpcsistemas.customeraccount.tenant.domain.model.WebhookSignature;
import java.time.Clock;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Outbox de webhooks: enfileira, assina (HMAC), entrega com retentativas e publica o desfecho. */
@Service
class TenantNotificationService implements TenantNotifications, DispatchNotificationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(TenantNotificationService.class);

    static final String HEADER_EVENT_ID = "X-CA-Event-Id";
    static final String HEADER_EVENT_TYPE = "X-CA-Event-Type";
    static final String HEADER_TIMESTAMP = "X-CA-Timestamp";
    static final String HEADER_SIGNATURE = "X-CA-Signature";

    private final NotificationQueuePort queue;
    private final WebhookEndpointRepositoryPort endpoints;
    private final WebhookTargetGuardPort guard;
    private final WebhookSenderPort sender;
    private final TenantEventsPublisherPort events;
    private final DeliveryPolicy policy;
    private final Clock clock;

    TenantNotificationService(
            NotificationQueuePort queue,
            WebhookEndpointRepositoryPort endpoints,
            WebhookTargetGuardPort guard,
            WebhookSenderPort sender,
            TenantEventsPublisherPort events,
            DeliveryPolicy policy,
            Clock clock) {
        this.queue = queue;
        this.endpoints = endpoints;
        this.guard = guard;
        this.sender = sender;
        this.events = events;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public void enqueue(TenantNotification n) {
        var active = endpoints.find(n.tenantId()).filter(w -> w.endpoint().enabled());
        if (active.isEmpty()) {
            log.atDebug()
                    .addKeyValue("tenant.id", n.tenantId())
                    .addKeyValue("notification.type", n.eventType())
                    .log("Tenant sem webhook ativo; notificação não enfileirada");
            return;
        }
        queue.enqueue(new QueuedNotification(
                n.eventId(),
                n.tenantId(),
                n.eventType(),
                n.reference(),
                n.subjectId(),
                n.payloadJson(),
                0,
                n.occurredAt()));
    }

    @Override
    public int dispatchDue(int batchSize) {
        var due = queue.claimDue(clock.instant(), batchSize);
        due.forEach(this::deliver);
        return due.size();
    }

    private void deliver(QueuedNotification n) {
        var webhook = endpoints.find(n.tenantId()).filter(w -> w.endpoint().enabled());
        if (webhook.isEmpty()) {
            queue.markDiscarded(n.eventId(), "WEBHOOK_NOT_CONFIGURED", clock.instant());
            return;
        }
        int attempt = n.attempts() + 1;
        var url = webhook.get().endpoint().url();
        long timestamp = clock.instant().getEpochSecond();
        var headers = new LinkedHashMap<String, String>();
        headers.put(HEADER_EVENT_ID, n.eventId());
        headers.put(HEADER_EVENT_TYPE, n.eventType());
        headers.put(HEADER_TIMESTAMP, Long.toString(timestamp));
        headers.put(HEADER_SIGNATURE, WebhookSignature.sign(webhook.get().secret(), timestamp, n.payload()));

        int status;
        try {
            guard.ensureAllowed(url);
            status = sender.send(url, headers, n.payload());
        } catch (InvalidValueException ex) {
            fail(n, attempt, null, "TARGET_NOT_ALLOWED");
            return;
        } catch (WebhookSendException ex) {
            fail(n, attempt, null, ex.errorCode());
            return;
        }

        if (status >= 200 && status < 300) {
            queue.markDelivered(n.eventId(), attempt, status, clock.instant());
            events.delivered(new TenantNotificationDelivered(
                    n.tenantId(), n.eventId(), n.eventType(), n.reference(), status, attempt));
            log.atInfo()
                    .addKeyValue("tenant.id", n.tenantId())
                    .addKeyValue("notification.id", n.eventId())
                    .addKeyValue("notification.attempt", attempt)
                    .addKeyValue("http.status", status)
                    .log("Notificação entregue ao webhook do tenant");
        } else {
            fail(n, attempt, status, "HTTP_" + status);
        }
    }

    private void fail(QueuedNotification n, int attempt, Integer status, String error) {
        var now = clock.instant();
        if (policy.exhausted(attempt)) {
            queue.markFailed(n.eventId(), attempt, status, error, now);
            events.failed(new TenantNotificationFailed(
                    n.tenantId(), n.eventId(), n.eventType(), n.reference(), attempt, status));
            log.atError()
                    .addKeyValue("tenant.id", n.tenantId())
                    .addKeyValue("notification.id", n.eventId())
                    .addKeyValue("notification.error", error)
                    .log("Notificação descartada após esgotar as tentativas de entrega");
            return;
        }
        var next = now.plus(policy.delayAfter(attempt));
        queue.reschedule(n.eventId(), attempt, status, error, next, now);
        log.atWarn()
                .addKeyValue("tenant.id", n.tenantId())
                .addKeyValue("notification.id", n.eventId())
                .addKeyValue("notification.attempt", attempt)
                .addKeyValue("notification.error", error)
                .addKeyValue("notification.nextAttemptAt", next)
                .log("Falha ao entregar notificação; nova tentativa agendada");
    }
}
