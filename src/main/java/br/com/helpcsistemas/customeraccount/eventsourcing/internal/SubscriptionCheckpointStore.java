package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import io.kurrent.dbclient.Position;
import java.time.Clock;
import java.util.Optional;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Persiste a última posição processada no {@code $all} por subscription, para retomar após restart. */
@Component
class SubscriptionCheckpointStore {

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
