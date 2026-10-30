package br.com.helpcsistemas.customeraccount.verification.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderFailure;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationOutcome;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** Resultado padronizado das validações. Sem dados cadastrais do titular (apenas o id pseudônimo). */
@Component
class MongoVerificationViewRepository implements VerificationViewRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoVerificationViewRepository.class);
    static final String COLLECTION = "verification_views";

    private final MongoTemplate mongo;
    private final PersonalDataCipher cipher;

    MongoVerificationViewRepository(MongoTemplate mongo, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.cipher = cipher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            var indexes = mongo.indexOps(COLLECTION);
            indexes.createIndex(new Index()
                    .on("tenantId", Sort.Direction.ASC)
                    .on("customerId", Sort.Direction.ASC)
                    .on("requestedAt", Sort.Direction.DESC)
                    .named("ix_tenant_customer"));
            indexes.createIndex(new Index().on("customerId", Sort.Direction.ASC).named("ix_customer"));
            indexes.createIndex(new Index()
                    .on("status", Sort.Direction.ASC)
                    .on("challengeExpiresAt", Sort.Direction.ASC)
                    .on("hostedLinkExpiresAt", Sort.Direction.ASC)
                    .named("ix_awaiting_expiration"));
            indexes.createIndex(new Index()
                    .on("outcome", Sort.Direction.ASC)
                    .on("validUntil", Sort.Direction.ASC)
                    .named("ix_validity_expiration"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar índices de verification_views");
        }
    }

    @Override
    public Optional<VerificationView> findById(VerificationId id) {
        return Optional.ofNullable(mongo.findById(id.toString(), VerificationDocument.class, COLLECTION))
                .map(document -> document.toView(cipher));
    }

    @Override
    public List<VerificationView> findByTenantAndCustomer(TenantId tenant, UUID customerId) {
        var query = Query.query(Criteria.where("tenantId")
                        .is(tenant.value())
                        .and("customerId")
                        .is(customerId.toString()))
                .with(Sort.by(Sort.Direction.DESC, "requestedAt"))
                .limit(100);
        return mongo.find(query, VerificationDocument.class, COLLECTION).stream()
                .map(document -> document.toView(cipher))
                .toList();
    }

    @Override
    public void save(VerificationView view) {
        mongo.save(VerificationDocument.from(view, cipher), COLLECTION);
    }

    @Override
    public void deleteByCustomer(UUID customerId) {
        mongo.remove(Query.query(Criteria.where("customerId").is(customerId.toString())), COLLECTION);
    }

    @Override
    public List<VerificationId> findAwaitingExpiredBefore(Instant instant, int limit) {
        var query = Query.query(new Criteria()
                        .orOperator(
                                Criteria.where("status")
                                        .is(VerificationStatus.AWAITING_CONFIRMATION.name())
                                        .and("challengeExpiresAt")
                                        .lte(instant),
                                Criteria.where("status")
                                        .is(VerificationStatus.AWAITING_HOLDER.name())
                                        .and("hostedLinkExpiresAt")
                                        .lte(instant.minus(VerificationProcess.HOSTED_LINK_GRACE_PERIOD))))
                .limit(limit);
        query.fields().include("_id");
        return mongo.find(query, org.bson.Document.class, COLLECTION).stream()
                .map(d -> VerificationId.of(d.getString("_id")))
                .toList();
    }

    @Override
    public List<VerificationId> findValidityExpiredBefore(Instant instant, int limit) {
        var query = Query.query(Criteria.where("outcome")
                        .is(VerificationOutcome.APPROVED.name())
                        .and("validUntil")
                        .lte(instant)
                        .and("validityExpired")
                        .ne(true))
                .limit(limit);
        query.fields().include("_id");
        return mongo.find(query, org.bson.Document.class, COLLECTION).stream()
                .map(d -> VerificationId.of(d.getString("_id")))
                .toList();
    }

    record FailureDocument(String provider, String errorCode, long latencyMs, Instant occurredAt) {}

    record VerificationDocument(
            @Id String id,
            String tenantId,
            String customerId,
            String type,
            String status,
            String outcome,
            String provider,
            Integer score,
            List<String> reasonCodes,
            String providerTransactionId,
            long policyVersion,
            boolean failedOver,
            List<FailureDocument> failures,
            Instant requestedAt,
            Instant concludedAt,
            Instant challengeExpiresAt,
            int challengeAttempts,
            Instant deliveredAt,
            boolean deliveryFailed,
            byte[] hostedLink,
            Instant hostedLinkExpiresAt,
            Instant validUntil,
            boolean validityExpired,
            boolean reused,
            long version) {

        static VerificationDocument from(VerificationView v, PersonalDataCipher cipher) {
            return new VerificationDocument(
                    v.id().toString(),
                    v.tenantId().value(),
                    v.customerId().toString(),
                    v.type().name(),
                    v.status().name(),
                    v.outcome() == null ? null : v.outcome().name(),
                    v.provider().value(),
                    v.score(),
                    v.reasonCodes(),
                    v.providerTransactionId(),
                    v.policyVersion(),
                    v.failedOver(),
                    v.failures().stream()
                            .map(f -> new FailureDocument(
                                    f.provider().value(), f.errorCode(), f.latencyMs(), f.occurredAt()))
                            .toList(),
                    v.requestedAt(),
                    v.concludedAt(),
                    v.challengeExpiresAt(),
                    v.challengeAttempts(),
                    v.deliveredAt(),
                    v.deliveryFailed(),
                    v.hostedLink() == null
                            ? null
                            : cipher.encrypt(
                                    v.customerId().toString(), v.hostedLink().getBytes(StandardCharsets.UTF_8)),
                    v.hostedLinkExpiresAt(),
                    v.validUntil(),
                    v.validityExpired(),
                    v.reused(),
                    v.version());
        }

        VerificationView toView(PersonalDataCipher cipher) {
            return new VerificationView(
                    VerificationId.of(id),
                    TenantId.of(tenantId),
                    UUID.fromString(customerId),
                    VerificationType.valueOf(type),
                    VerificationStatus.valueOf(status),
                    outcome == null ? null : VerificationOutcome.valueOf(outcome),
                    new ProviderId(provider),
                    score,
                    reasonCodes,
                    providerTransactionId,
                    policyVersion,
                    failedOver,
                    failures == null
                            ? List.of()
                            : failures.stream()
                                    .map(f -> new ProviderFailure(
                                            new ProviderId(f.provider()), f.errorCode(), f.latencyMs(), f.occurredAt()))
                                    .toList(),
                    requestedAt,
                    concludedAt,
                    challengeExpiresAt,
                    challengeAttempts,
                    deliveredAt,
                    deliveryFailed,
                    hostedLink == null
                            ? null
                            : new String(cipher.decrypt(customerId, hostedLink), StandardCharsets.UTF_8),
                    hostedLinkExpiresAt,
                    validUntil,
                    validityExpired,
                    reused,
                    version);
        }
    }
}
