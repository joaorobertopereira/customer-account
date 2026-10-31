package br.com.helpcsistemas.customeraccount.verification.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderCallbackInboxPort;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/** Durable inbox: callback raw data is encrypted and deleted after seven days. */
@Component
class MongoProviderCallbackInbox implements ProviderCallbackInboxPort {

    private static final Logger log = LoggerFactory.getLogger(MongoProviderCallbackInbox.class);
    static final String COLLECTION = "verification_callback_inbox";
    static final Duration LEASE = Duration.ofMinutes(1);
    static final Duration RETENTION = Duration.ofDays(7);
    static final int MAX_ATTEMPTS = 8;

    private final MongoTemplate mongo;
    private final PersonalDataCipher cipher;

    MongoProviderCallbackInbox(MongoTemplate mongo, PersonalDataCipher cipher) {
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
                    .named("ix_callback_status_next"));
            indexes.createIndex(new Index()
                    .on("purgeAt", Sort.Direction.ASC)
                    .expire(Duration.ZERO)
                    .named("ttl_callback"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar índices da inbox de callbacks");
        }
    }

    @Override
    public boolean enqueue(ProviderId provider, String eventId, UUID customerId, byte[] payload, Instant receivedAt) {
        var id = provider.value() + ":" + eventId;
        var subject = subject(provider);
        try {
            mongo.insert(
                    new CallbackDocument(
                            id,
                            provider.value(),
                            eventId,
                            customerId == null ? null : customerId.toString(),
                            cipher.encrypt(subject, payload),
                            Status.PENDING.name(),
                            0,
                            receivedAt,
                            receivedAt,
                            null,
                            null,
                            receivedAt,
                            null),
                    COLLECTION);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public List<Callback> claimDue(Instant now, int limit) {
        var claimed = new ArrayList<Callback>();
        while (claimed.size() < limit) {
            var query = new Query(new Criteria()
                            .orOperator(
                                    Criteria.where("status")
                                            .is(Status.PENDING.name())
                                            .and("nextAttemptAt")
                                            .lte(now),
                                    Criteria.where("status")
                                            .is(Status.PROCESSING.name())
                                            .and("leaseUntil")
                                            .lte(now)))
                    .with(Sort.by("nextAttemptAt"));
            var doc = mongo.findAndModify(
                    query,
                    new Update().set("status", Status.PROCESSING.name()).set("leaseUntil", now.plus(LEASE)),
                    FindAndModifyOptions.options().returnNew(true),
                    CallbackDocument.class,
                    COLLECTION);
            if (doc == null) {
                break;
            }
            var payload = cipher.decrypt(subject(ProviderId.of(doc.provider())), doc.payload());
            claimed.add(new Callback(doc.id(), ProviderId.of(doc.provider()), doc.eventId(), payload, doc.attempts()));
        }
        return claimed;
    }

    @Override
    public void markProcessed(String id, Instant at) {
        finish(id, Status.PROCESSED, at);
    }

    @Override
    public void markUnmatched(String id, Instant at) {
        finish(id, Status.UNMATCHED, at);
    }

    @Override
    public void retry(String id, int attempts, Instant nextAttemptAt, Instant at) {
        if (attempts >= MAX_ATTEMPTS) {
            finish(id, Status.FAILED, at);
            return;
        }
        mongo.updateFirst(
                byId(id),
                new Update()
                        .set("status", Status.PENDING.name())
                        .set("attempts", attempts)
                        .set("nextAttemptAt", nextAttemptAt)
                        .unset("leaseUntil"),
                COLLECTION);
    }

    @Override
    public void deleteByCustomer(UUID customerId) {
        mongo.remove(Query.query(Criteria.where("customerId").is(customerId.toString())), COLLECTION);
    }

    private void finish(String id, Status status, Instant at) {
        mongo.updateFirst(
                byId(id),
                new Update()
                        .set("status", status.name())
                        .unset("payload")
                        .unset("leaseUntil")
                        .set("updatedAt", at)
                        .set("purgeAt", at.plus(RETENTION)),
                COLLECTION);
    }

    private static Query byId(String id) {
        return Query.query(Criteria.where("_id").is(id));
    }

    private static String subject(ProviderId provider) {
        return "provider-callback:" + provider.value();
    }

    enum Status {
        PENDING,
        PROCESSING,
        PROCESSED,
        UNMATCHED,
        FAILED
    }

    record CallbackDocument(
            @Id String id,
            String provider,
            String eventId,
            String customerId,
            byte[] payload,
            String status,
            int attempts,
            Instant receivedAt,
            Instant nextAttemptAt,
            Instant leaseUntil,
            String lastError,
            Instant updatedAt,
            Instant purgeAt) {}
}
