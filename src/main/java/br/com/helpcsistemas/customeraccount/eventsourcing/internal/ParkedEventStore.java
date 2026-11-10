package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** Persistência dos eventos estacionados ({@code eventstore_parked_events}), um documento por subscription+evento. */
@Component
class ParkedEventStore {

    static final String COLLECTION = "eventstore_parked_events";

    record Entry(String id, String subscription, StoredEvent event, String error, int attempts, Instant parkedAt) {}

    private final MongoTemplate mongo;
    private final Clock clock;

    ParkedEventStore(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    static String idOf(String subscription, UUID eventId) {
        return subscription + ":" + eventId;
    }

    void park(String subscription, StoredEvent event, String error, int attempts) {
        var doc = new Document("_id", idOf(subscription, event.eventId()))
                .append("subscription", subscription)
                .append("stream", event.stream())
                .append("revision", event.revision())
                .append("eventId", event.eventId().toString())
                .append("eventType", event.type())
                .append("data", event.data())
                .append("metadata", new Document(new HashMap<String, Object>(event.metadata())))
                .append("recordedAt", event.recordedAt())
                .append("error", error)
                .append("attempts", attempts)
                .append("parkedAt", clock.instant());
        mongo.save(doc, COLLECTION);
    }

    List<Entry> findAll() {
        return mongo.find(Query.query(new Criteria()).with(Sort.by("parkedAt")), Document.class, COLLECTION).stream()
                .map(ParkedEventStore::toEntry)
                .toList();
    }

    Optional<Entry> find(String id) {
        return Optional.ofNullable(mongo.findById(id, Document.class, COLLECTION))
                .map(ParkedEventStore::toEntry);
    }

    void remove(String id) {
        mongo.remove(Query.query(Criteria.where("_id").is(id)), COLLECTION);
    }

    @SuppressWarnings("unchecked")
    private static Entry toEntry(Document d) {
        Map<String, String> metadata = new HashMap<>();
        ((Map<String, Object>) d.get("metadata")).forEach((k, v) -> metadata.put(k, String.valueOf(v)));
        var data = d.get("data", Binary.class).getData();
        var event = new StoredEvent(
                d.getString("stream"),
                d.getLong("revision"),
                UUID.fromString(d.getString("eventId")),
                d.getString("eventType"),
                data,
                metadata,
                d.getDate("recordedAt").toInstant());
        return new Entry(
                d.getString("_id"),
                d.getString("subscription"),
                event,
                d.getString("error"),
                d.getInteger("attempts"),
                d.getDate("parkedAt").toInstant());
    }
}
