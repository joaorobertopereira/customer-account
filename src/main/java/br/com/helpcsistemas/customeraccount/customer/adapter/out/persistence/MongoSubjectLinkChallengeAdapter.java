package br.com.helpcsistemas.customeraccount.customer.adapter.out.persistence;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkChallengePort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkChallengePort.Challenge;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

@Component
class MongoSubjectLinkChallengeAdapter implements SubjectLinkChallengePort {

    static final String COLLECTION = "subject_link_otp_challenges";
    private static final int MAX_ATTEMPTS = 5;
    private static final Logger log = LoggerFactory.getLogger(MongoSubjectLinkChallengeAdapter.class);

    private final MongoTemplate mongo;

    MongoSubjectLinkChallengeAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            var indexes = mongo.indexOps(COLLECTION);
            indexes.createIndex(new Index()
                    .on("expiresAt", Sort.Direction.ASC)
                    .expire(Duration.ZERO)
                    .named("ttl_subject_link_otp"));
            indexes.createIndex(new Index()
                    .on("subject", Sort.Direction.ASC)
                    .on("tenant.value", Sort.Direction.ASC)
                    .on("createdAt", Sort.Direction.DESC)
                    .named("ix_subject_link_rate_limit"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar índices dos desafios de vínculo");
        }
    }

    @Override
    public void save(Challenge challenge) {
        mongo.insert(
                new ChallengeDocument(
                        challenge.id(),
                        challenge.subject(),
                        challenge.tenant(),
                        challenge.customerId().toString(),
                        challenge.codeHash(),
                        challenge.createdAt(),
                        challenge.expiresAt(),
                        challenge.attempts()),
                COLLECTION);
    }

    @Override
    public Optional<Challenge> findLatestForSubject(String subject, TenantId tenant, Instant now) {
        var query = Query.query(Criteria.where("subject")
                        .is(subject)
                        .and("tenant.value")
                        .is(tenant.value())
                        .and("expiresAt")
                        .gt(now))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .limit(1);
        return Optional.ofNullable(mongo.findOne(query, ChallengeDocument.class, COLLECTION))
                .map(ChallengeDocument::toChallenge);
    }

    @Override
    public Optional<Challenge> find(String id, String subject, TenantId tenant, Instant now) {
        var query = Query.query(Criteria.where("_id")
                .is(id)
                .and("subject")
                .is(subject)
                .and("tenant.value")
                .is(tenant.value())
                .and("expiresAt")
                .gt(now)
                .and("attempts")
                .lt(MAX_ATTEMPTS));
        return Optional.ofNullable(mongo.findOne(query, ChallengeDocument.class, COLLECTION))
                .map(ChallengeDocument::toChallenge);
    }

    @Override
    public Optional<Challenge> consume(String id, String subject, TenantId tenant, String codeHash, Instant now) {
        var query = Query.query(Criteria.where("_id")
                .is(id)
                .and("subject")
                .is(subject)
                .and("tenant.value")
                .is(tenant.value())
                .and("codeHash")
                .is(codeHash)
                .and("expiresAt")
                .gt(now)
                .and("attempts")
                .lt(MAX_ATTEMPTS));
        return Optional.ofNullable(mongo.findAndRemove(query, ChallengeDocument.class, COLLECTION))
                .map(ChallengeDocument::toChallenge);
    }

    @Override
    public void recordFailedAttempt(String id, String subject, TenantId tenant, Instant now) {
        var query = Query.query(Criteria.where("_id")
                .is(id)
                .and("subject")
                .is(subject)
                .and("tenant.value")
                .is(tenant.value())
                .and("expiresAt")
                .gt(now)
                .and("attempts")
                .lt(MAX_ATTEMPTS));
        mongo.updateFirst(query, new Update().inc("attempts", 1), COLLECTION);
    }

    @Override
    public void delete(String id) {
        mongo.remove(Query.query(Criteria.where("_id").is(id)), COLLECTION);
    }

    record ChallengeDocument(
            @Id String id,
            String subject,
            TenantId tenant,
            String customerId,
            String codeHash,
            Instant createdAt,
            Instant expiresAt,
            int attempts) {

        Challenge toChallenge() {
            return new Challenge(
                    id,
                    subject,
                    tenant,
                    java.util.UUID.fromString(customerId),
                    codeHash,
                    createdAt,
                    expiresAt,
                    attempts);
        }
    }
}
