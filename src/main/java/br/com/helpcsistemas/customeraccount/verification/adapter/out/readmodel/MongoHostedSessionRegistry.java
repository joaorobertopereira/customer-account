package br.com.helpcsistemas.customeraccount.verification.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort.HostedSessionReference;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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

/** Mongo leases prevent concurrent front requests from creating multiple hosted sessions for the same tenant/customer. */
@Component
class MongoHostedSessionRegistry implements HostedSessionRegistryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoHostedSessionRegistry.class);
    static final String ACTIVE_COLLECTION = "verification_active_sessions";
    static final String CORRELATION_COLLECTION = "verification_session_index";
    static final Duration LEASE = Duration.ofMinutes(2);

    private final MongoTemplate mongo;

    MongoHostedSessionRegistry(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            mongo.indexOps(ACTIVE_COLLECTION)
                    .createIndex(new Index()
                            .on("purgeAt", Sort.Direction.ASC)
                            .expire(Duration.ZERO)
                            .named("ttl_session"));
            mongo.indexOps(CORRELATION_COLLECTION)
                    .createIndex(new Index()
                            .on("provider", Sort.Direction.ASC)
                            .on("sessionId", Sort.Direction.ASC)
                            .unique()
                            .named("ux_provider_session"));
            mongo.indexOps(CORRELATION_COLLECTION)
                    .createIndex(new Index()
                            .on("purgeAt", Sort.Direction.ASC)
                            .expire(Duration.ZERO)
                            .named("ttl_correlation"));
            mongo.indexOps(CORRELATION_COLLECTION)
                    .createIndex(
                            new Index().on("customerId", Sort.Direction.ASC).named("ix_session_customer"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar índices de sessões hospedadas");
        }
    }

    @Override
    public Optional<HostedSessionReference> findActive(
            TenantId tenant, UUID customerId, VerificationType type, Instant now) {
        var doc = mongo.findById(activeId(tenant, customerId, type), ActiveSessionDocument.class, ACTIVE_COLLECTION);
        if (doc == null || doc.expiresAt() == null || !doc.expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return Optional.of(doc.toReference());
    }

    @Override
    public boolean claim(
            TenantId tenant,
            UUID customerId,
            VerificationType type,
            VerificationId verificationId,
            Instant leaseUntil,
            Instant now) {
        var query = Query.query(Criteria.where("_id")
                .is(activeId(tenant, customerId, type))
                .and("expiresAt")
                .lte(now));
        var update = new Update()
                .set("tenantId", tenant.value())
                .set("customerId", customerId.toString())
                .set("type", type.name())
                .set("verificationId", verificationId.toString())
                .set("provider", null)
                .set("sessionId", null)
                .set("expiresAt", leaseUntil)
                .set("purgeAt", leaseUntil.plus(Duration.ofDays(1)))
                .set("ready", false);
        try {
            mongo.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    ActiveSessionDocument.class,
                    ACTIVE_COLLECTION);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public void activate(HostedSessionReference session) {
        mongo.updateFirst(
                Query.query(Criteria.where("_id")
                        .is(activeId(session.tenant(), session.customerId(), session.type()))
                        .and("verificationId")
                        .is(session.verificationId().toString())),
                new Update()
                        .set("provider", session.provider().value())
                        .set("sessionId", session.sessionId())
                        .set("expiresAt", session.expiresAt())
                        .set(
                                "purgeAt",
                                session.expiresAt()
                                        .plus(
                                                br.com.helpcsistemas.customeraccount.verification.domain.model
                                                        .VerificationProcess.HOSTED_LINK_GRACE_PERIOD)
                                        .plus(Duration.ofDays(1)))
                        .set("ready", true),
                ACTIVE_COLLECTION);
        mongo.save(
                new CorrelationDocument(
                        correlationId(session.provider(), session.sessionId()),
                        session.provider().value(),
                        session.sessionId(),
                        session.verificationId().toString(),
                        session.tenant().value(),
                        session.customerId().toString(),
                        session.expiresAt()
                                .plus(
                                        br.com.helpcsistemas.customeraccount.verification.domain.model
                                                .VerificationProcess.HOSTED_LINK_GRACE_PERIOD)
                                .plus(Duration.ofDays(1))),
                CORRELATION_COLLECTION);
    }

    @Override
    public Optional<HostedSessionReference> findByProviderSession(ProviderId provider, String sessionId) {
        var doc = mongo.findById(correlationId(provider, sessionId), CorrelationDocument.class, CORRELATION_COLLECTION);
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(new HostedSessionReference(
                TenantId.of(doc.tenantId()),
                UUID.fromString(doc.customerId()),
                VerificationType.IDENTITY,
                VerificationId.of(doc.verificationId()),
                provider,
                sessionId,
                null,
                true));
    }

    @Override
    public void release(VerificationId verificationId) {
        mongo.remove(Query.query(Criteria.where("verificationId").is(verificationId.toString())), ACTIVE_COLLECTION);
    }

    @Override
    public void deleteByCustomer(UUID customerId) {
        mongo.remove(Query.query(Criteria.where("customerId").is(customerId.toString())), ACTIVE_COLLECTION);
        mongo.remove(Query.query(Criteria.where("customerId").is(customerId.toString())), CORRELATION_COLLECTION);
    }

    private static String activeId(TenantId tenant, UUID customerId, VerificationType type) {
        return tenant.value() + ":" + customerId + ":" + type;
    }

    private static String correlationId(ProviderId provider, String sessionId) {
        return provider.value() + ":" + sessionId;
    }

    record ActiveSessionDocument(
            @Id String id,
            String tenantId,
            String customerId,
            String type,
            String verificationId,
            String provider,
            String sessionId,
            Instant expiresAt,
            Instant purgeAt,
            boolean ready) {

        HostedSessionReference toReference() {
            return new HostedSessionReference(
                    TenantId.of(tenantId),
                    UUID.fromString(customerId),
                    VerificationType.valueOf(type),
                    VerificationId.of(verificationId),
                    provider == null ? null : ProviderId.of(provider),
                    sessionId,
                    expiresAt,
                    ready);
        }
    }

    record CorrelationDocument(
            @Id String id,
            String provider,
            String sessionId,
            String verificationId,
            String tenantId,
            String customerId,
            Instant purgeAt) {}
}
