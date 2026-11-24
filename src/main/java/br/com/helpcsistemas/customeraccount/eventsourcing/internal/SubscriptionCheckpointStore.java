package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import io.kurrent.dbclient.Position;
import java.time.Clock;
import java.util.Optional;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Persiste a última posição processada no {@code $all} por subscription, para retomar após restart. */
@Component
class SubscriptionCheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCheckpointStore.class);
    static final String COLLECTION = "eventstore_subscription_checkpoints";

    private final MongoTemplate mongo;
    private final Clock clock;

    SubscriptionCheckpointStore(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    Optional<Position> load(String subscriptionName) {
        var doc = mongo.findById(subscriptionName, Document.class, COLLECTION);
        return Optional.ofNullable(doc).map(d -> new Position(d.getLong("commit"), d.getLong("prepare")));
    }

    /**
     * Checkpoint próprio ou, na primeira execução, o da subscription de origem. O herdado é gravado como próprio para
     * que uma reconstrução posterior da origem não volte a afetá-lo.
     */
    Optional<Position> loadOrInherit(String subscriptionName, Optional<String> inheritFrom) {
        var own = load(subscriptionName);
        if (own.isPresent() || inheritFrom.isEmpty()) {
            return own;
        }
        var inherited = load(inheritFrom.get());
        inherited.ifPresent(position -> {
            save(subscriptionName, position);
            log.atInfo()
                    .addKeyValue("subscription.name", subscriptionName)
                    .addKeyValue("subscription.inheritedFrom", inheritFrom.get())
                    .log("Checkpoint herdado na primeira execução");
        });
        return inherited;
    }

    void delete(String subscriptionName) {
        mongo.remove(Query.query(Criteria.where("_id").is(subscriptionName)), COLLECTION);
    }

    void save(String subscriptionName, Position position) {
        mongo.upsert(
                Query.query(Criteria.where("_id").is(subscriptionName)),
                new Update()
                        .set("commit", position.getCommitUnsigned())
                        .set("prepare", position.getPrepareUnsigned())
                        .set("updatedAt", clock.instant()),
                COLLECTION);
    }
}
