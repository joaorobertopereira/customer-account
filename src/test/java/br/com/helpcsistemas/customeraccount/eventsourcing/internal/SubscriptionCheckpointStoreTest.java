package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

class SubscriptionCheckpointStoreTest {

    private static final String COLLECTION = SubscriptionCheckpointStore.COLLECTION;

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final SubscriptionCheckpointStore store =
            new SubscriptionCheckpointStore(mongo, Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void ownCheckpointWins() {
        when(mongo.findById("notifier", Document.class, COLLECTION)).thenReturn(position(5));

        var checkpoint = store.loadOrInherit("notifier", Optional.of("views"));

        assertThat(checkpoint)
                .hasValueSatisfying(p -> assertThat(p.getCommitUnsigned()).isEqualTo(5));
        verify(mongo, never()).findById(eq("views"), eq(Document.class), eq(COLLECTION));
    }

    @Test
    void firstRunInheritsAndPersistsTheSourcePosition() {
        when(mongo.findById("views", Document.class, COLLECTION)).thenReturn(position(42));

        var checkpoint = store.loadOrInherit("notifier", Optional.of("views"));

        assertThat(checkpoint)
                .hasValueSatisfying(p -> assertThat(p.getCommitUnsigned()).isEqualTo(42));
        verify(mongo).upsert(any(Query.class), any(Update.class), eq(COLLECTION));
    }

    @Test
    void withoutSourceCheckpointStartsFromTheBeginning() {
        assertThat(store.loadOrInherit("notifier", Optional.of("views"))).isEmpty();
        assertThat(store.loadOrInherit("notifier", Optional.empty())).isEmpty();
        verify(mongo, never()).upsert(any(Query.class), any(Update.class), eq(COLLECTION));
    }

    private static Document position(long value) {
        return new Document("commit", value).append("prepare", value);
    }
}
