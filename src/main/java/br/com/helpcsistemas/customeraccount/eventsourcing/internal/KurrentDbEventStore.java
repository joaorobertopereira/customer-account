package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
import io.kurrent.dbclient.AppendToStreamOptions;
import io.kurrent.dbclient.EventData;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.ReadStreamOptions;
import io.kurrent.dbclient.StreamNotFoundException;
import io.kurrent.dbclient.StreamState;
import io.kurrent.dbclient.WrongExpectedVersionException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Implementação do {@link EventStore} sobre o client gRPC do KurrentDB. Cada operação gera span + métrica. */
@Component
class KurrentDbEventStore implements EventStore {

    private static final Logger log = LoggerFactory.getLogger(KurrentDbEventStore.class);

    private final KurrentDBClient client;
    private final RecordedEventMapper mapper;
    private final TraceContextPropagation tracing;
    private final ObservationRegistry observations;
    private final long timeoutMillis;

    KurrentDbEventStore(
            KurrentDBClient client,
            RecordedEventMapper mapper,
            TraceContextPropagation tracing,
            ObservationRegistry observations,
            EventStoreProperties properties) {
        this.client = client;
        this.mapper = mapper;
        this.tracing = tracing;
        this.observations = observations;
        this.timeoutMillis = properties.operationTimeout().toMillis();
    }

    @Override
    public long append(String stream, ExpectedRevision expected, List<NewEvent> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Nenhum evento para gravar");
        }
        var data = events.stream().map(this::toEventData).toList();
        var options = AppendToStreamOptions.get().streamState(toStreamState(expected));

        return observation("eventstore.append", stream).observe(() -> {
            try {
                var result = await(client.appendToStream(stream, options, data.iterator()));
                long revision = result.getNextExpectedRevision().toRawLong();
                log.atDebug()
                        .addKeyValue("eventstore.category", category(stream))
                        .addKeyValue("eventstore.revision", revision)
                        .addKeyValue("eventstore.eventCount", events.size())
                        .log("Eventos gravados no event store");
                return revision;
            } catch (WrongExpectedVersionException ex) {
                log.atWarn()
                        .addKeyValue("eventstore.category", category(stream))
                        .log("Conflito de revisão ao gravar no stream");
                throw new StreamRevisionConflictException("Stream alterado concorrentemente", ex);
            }
        });
    }

    @Override
    public List<StoredEvent> readStream(String stream) {
        return observation("eventstore.read", stream).observe(() -> {
            try {
                var result = await(client.readStream(
                        stream, ReadStreamOptions.get().forwards().fromStart()));
                return result.getEvents().stream()
                        .map(e -> mapper.toStored(e.getOriginalEvent()))
                        .toList();
            } catch (StreamNotFoundException ex) {
                return List.of();
            }
        });
    }

    @Override
    public Optional<StoredEvent> readLastEvent(String stream) {
        return observation("eventstore.read-last", stream).observe(() -> {
            try {
                var result = await(client.readStream(
                        stream, ReadStreamOptions.get().backwards().fromEnd().maxCount(1)));
                return result.getEvents().stream().findFirst().map(e -> mapper.toStored(e.getOriginalEvent()));
            } catch (StreamNotFoundException ex) {
                return Optional.empty();
            }
        });
    }

    private EventData toEventData(NewEvent event) {
        var builder = event.json()
                ? EventData.builderAsJson(event.eventId(), event.type(), event.data())
                : EventData.builderAsBinary(event.eventId(), event.type(), event.data());
        return builder.metadataAsBytes(mapper.encodeMetadata(tracing.inject(event.metadata())))
                .build();
    }

    private static StreamState toStreamState(ExpectedRevision expected) {
        return switch (expected) {
            case ExpectedRevision.Any _ -> StreamState.any();
            case ExpectedRevision.NoStream _ -> StreamState.noStream();
            case ExpectedRevision.Exact exact -> StreamState.streamRevision(exact.revision());
        };
    }

    private Observation observation(String name, String stream) {
        return Observation.createNotStarted(name, observations)
                .lowCardinalityKeyValue("eventstore.category", category(stream));
    }

    /** Categoria = prefixo antes do primeiro '-' (ex.: {@code customer-<uuid>} → {@code customer}). */
    private static String category(String stream) {
        int idx = stream.indexOf('-');
        return idx > 0 ? stream.substring(0, idx) : stream;
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operação no event store interrompida", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Falha no event store", ex.getCause());
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timeout no event store", ex);
        }
    }
}
