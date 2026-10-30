package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.otp;

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
import org.springframework.stereotype.Component;

/** Desafios OTP pendentes. Guarda só o hash do código; o MongoDB apaga o documento ao expirar (índice TTL). */
@Component
class OtpChallengeStore {

    private static final Logger log = LoggerFactory.getLogger(OtpChallengeStore.class);
    static final String COLLECTION = "verification_otp_challenges";

    private final MongoTemplate mongo;

    OtpChallengeStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            mongo.indexOps(COLLECTION)
                    .createIndex(new Index()
                            .on("expiresAt", Sort.Direction.ASC)
                            .expire(java.time.Duration.ZERO)
                            .named("ttl_expires_at"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar índice TTL dos desafios OTP");
        }
    }

    public void save(OtpChallenge challenge) {
        mongo.insert(challenge, COLLECTION);
    }

    public Optional<OtpChallenge> find(String challengeId) {
        return Optional.ofNullable(mongo.findById(challengeId, OtpChallenge.class, COLLECTION));
    }

    public void delete(String challengeId) {
        mongo.remove(Query.query(Criteria.where("_id").is(challengeId)), COLLECTION);
    }

    record OtpChallenge(@Id String id, String verificationId, String codeHash, Instant expiresAt) {}
}
