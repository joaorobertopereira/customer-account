package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import br.com.helpcsistemas.customeraccount.eventsourcing.Snapshot;
import br.com.helpcsistemas.customeraccount.eventsourcing.SnapshotStore;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/** Snapshots em MongoDB: um documento por stream ({@code _id = stream}), sempre na revisão mais recente. */
@Component
class MongoSnapshotStore implements SnapshotStore {

    static final String COLLECTION = "eventstore_snapshots";

    private final MongoTemplate mongo;

    MongoSnapshotStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<Snapshot> load(String stream) {
        var doc = mongo.findById(stream, Document.class, COLLECTION);
        if (doc == null) {
            return Optional.empty();
        }
        var data = doc.get("data", Binary.class);
        return Optional.of(new Snapshot(
                stream,
                doc.getLong("revision"),
                doc.getInteger("schemaVersion"),
                doc.getString("subject"),
                data.getData(),
                doc.getDate("takenAt").toInstant()));
    }

    @Override
    public void save(Snapshot snapshot) {
        var query = Query.query(Criteria.where("_id").is(snapshot.stream()).and("revision").lt(snapshot.revision()));
        var update = new Update()
                .set("revision", snapshot.revision())
                .set("schemaVersion", snapshot.schemaVersion())
                .set("subject", snapshot.subject())
                .set("data", snapshot.data())
                .set("takenAt", snapshot.takenAt());
        try {
            mongo.upsert(query, update, COLLECTION);
        } catch (DuplicateKeyException ex) {
            // Já existe snapshot igual ou mais novo (corrida entre instâncias): nada a fazer.
        }
    }
}
