package br.com.helpcsistemas.customeraccount.tenant.adapter.out.persistence;

import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.NotificationQueuePort;
import br.com.helpcsistemas.customeraccount.tenant.application.query.DeliveryView;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Fila de webhooks no MongoDB. Payload cifrado com a chave do titular: se ele for eliminado antes da entrega, a
 * notificação é descartada. Itens finalizados são apagados automaticamente após 30 dias (índice TTL).
 */
@Component
class MongoNotificationQueue implements NotificationQueuePort {

    private static final Logger log = LoggerFactory.getLogger(MongoNotificationQueue.class);
    static final String COLLECTION = "tenant_notifications";
    static final Duration LEASE = Duration.ofMinutes(1);
    static final Duration RETENTION = Duration.ofDays(30);

    private final MongoTemplate mongo;
    private final PersonalDataCipher cipher;

    MongoNotificationQueue(MongoTemplate mongo, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.cipher = cipher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            var indexes = mongo.indexOps(COLLECTION);
            indexes.createIndex(new Index()
                    .on("status", Sort.Direction.ASC)
                    .on("nextAttemptAt", Sort.Direction.ASC)
                    .named("ix_status_next"));
            indexes.createIndex(new Index()
                    .on("tenantId", Sort.Direction.ASC)
                    .on("updatedAt", Sort.Direction.DESC)
                    .named("ix_tenant_updated"));
            indexes.createIndex(new Index()
                    .on("purgeAt", Sort.Direction.ASC)
                    .expire(Duration.ZERO)
                    .named("ttl_purge_at"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar índices da fila de notificações");
        }
    }

    @Override
    public void enqueue(QueuedNotification n) {
        try {
            mongo.insert(
                    new NotificationDocument(
                            n.eventId(),
                            n.tenantId().value(),
                            n.eventType(),
                            n.reference(),
                            n.subjectId(),
                            cipher.encrypt(n.subjectId(), n.payload().getBytes(StandardCharsets.UTF_8)),
                            Status.PENDING.name(),
                            0,
                            n.occurredAt(),
                            null,
                            null,
                            null,
                            n.occurredAt(),
                            n.occurredAt(),
                            null),
                    COLLECTION);
        } catch (DuplicateKeyException ex) {
            log.atDebug().addKeyValue("notification.id", n.eventId()).log("Notificação já enfileirada");
        }
    }

    @Override
    public List<QueuedNotification> claimDue(Instant now, int limit) {
        var claimed = new ArrayList<QueuedNotification>();
        while (claimed.size() < limit) {
            var query = new Query(new Criteria()
                            .orOperator(
                                    Criteria.where("status")
                                            .is(Status.PENDING.name())
                                            .and("nextAttemptAt")
                                            .lte(now),
                                    Criteria.where("status")
                                            .is(Status.IN_FLIGHT.name())
                                            .and("leaseUntil")
                                            .lte(now)))
                    .with(Sort.by("nextAttemptAt"));
            var update = new Update()
                    .set("status", Status.IN_FLIGHT.name())
                    .set("leaseUntil", now.plus(LEASE))
                    .set("updatedAt", now);
            var doc = mongo.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().returnNew(true),
                    NotificationDocument.class,
                    COLLECTION);
            if (doc == null) {
                break;
            }
            try {
                var payload = new String(cipher.decrypt(doc.subjectId(), doc.payload()), StandardCharsets.UTF_8);
                claimed.add(new QueuedNotification(
                        doc.id(),
                        TenantId.of(doc.tenantId()),
                        doc.eventType(),
                        doc.reference(),
                        doc.subjectId(),
                        payload,
                        doc.attempts(),
                        doc.createdAt()));
            } catch (PersonalDataErasedException ex) {
                markDiscarded(doc.id(), "SUBJECT_ERASED", now);
            }
        }
        return claimed;
    }

    @Override
    public void markDelivered(String eventId, int attempts, int httpStatus, Instant at) {
        finish(eventId, Status.DELIVERED, attempts, httpStatus, null, at);
    }

    @Override
    public void reschedule(
            String eventId, int attempts, Integer httpStatus, String error, Instant nextAttemptAt, Instant at) {
        mongo.updateFirst(
                byId(eventId),
                new Update()
                        .set("status", Status.PENDING.name())
                        .set("attempts", attempts)
                        .set("lastHttpStatus", httpStatus)
                        .set("lastError", error)
                        .set("nextAttemptAt", nextAttemptAt)
                        .unset("leaseUntil")
                        .set("updatedAt", at),
                COLLECTION);
    }

    @Override
    public void markFailed(String eventId, int attempts, Integer httpStatus, String error, Instant at) {
        finish(eventId, Status.FAILED, attempts, httpStatus, error, at);
    }

    @Override
    public void markDiscarded(String eventId, String reason, Instant at) {
        mongo.updateFirst(
                byId(eventId),
                new Update()
                        .set("status", Status.DISCARDED.name())
                        .set("lastError", reason)
                        .unset("payload")
                        .unset("leaseUntil")
                        .set("updatedAt", at)
                        .set("purgeAt", at.plus(RETENTION)),
                COLLECTION);
    }

    @Override
    public List<DeliveryView> recent(TenantId tenant, int limit) {
        var query = Query.query(Criteria.where("tenantId").is(tenant.value()))
                .with(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .limit(limit);
        return mongo.find(query, NotificationDocument.class, COLLECTION).stream()
                .map(d -> new DeliveryView(
                        d.id(),
                        d.eventType(),
                        d.reference(),
                        d.status(),
                        d.attempts(),
                        d.lastHttpStatus(),
                        d.lastError(),
                        Status.PENDING.name().equals(d.status()) ? d.nextAttemptAt() : null,
                        d.updatedAt()))
                .toList();
    }

    /** Ao finalizar, o payload é removido (minimização) e o registro expira após o período de retenção. */
    private void finish(String eventId, Status status, int attempts, Integer httpStatus, String error, Instant at) {
        mongo.updateFirst(
                byId(eventId),
                new Update()
                        .set("status", status.name())
                        .set("attempts", attempts)
                        .set("lastHttpStatus", httpStatus)
                        .set("lastError", error)
                        .unset("payload")
                        .unset("leaseUntil")
                        .set("updatedAt", at)
                        .set("purgeAt", at.plus(RETENTION)),
                COLLECTION);
    }

    private static Query byId(String eventId) {
        return Query.query(Criteria.where("_id").is(eventId));
    }

    enum Status {
        PENDING,
        IN_FLIGHT,
        DELIVERED,
        FAILED,
        DISCARDED
    }

    record NotificationDocument(
            @Id String id,
            String tenantId,
            String eventType,
            String reference,
            String subjectId,
            byte[] payload,
            String status,
            int attempts,
            Instant nextAttemptAt,
            Instant leaseUntil,
            Integer lastHttpStatus,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Instant purgeAt) {}
}
