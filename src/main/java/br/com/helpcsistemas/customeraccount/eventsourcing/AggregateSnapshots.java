package br.com.helpcsistemas.customeraccount.eventsourcing;

import br.com.helpcsistemas.customeraccount.shared.domain.DomainEvent;
import br.com.helpcsistemas.customeraccount.shared.domain.EventSourcedAggregate;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Snapshot de agregados a cada {@value #INTERVAL} eventos. O estado é serializado pelos campos do agregado (sem
 * expor getters), selado pelo chamador (cifra por titular) e guardado no {@link SnapshotStore}. Snapshot é só uma
 * otimização: qualquer falha ao gravar ou ler cai no replay completo do stream.
 */
@Component
public class AggregateSnapshots {

    private static final Logger log = LoggerFactory.getLogger(AggregateSnapshots.class);

    /** Um snapshot é tirado sempre que a contagem de eventos do stream cruza um múltiplo deste valor. */
    public static final int INTERVAL = 10;

    /** Incrementar quando o formato do estado de qualquer agregado mudar; snapshots antigos são então ignorados. */
    static final int SCHEMA_VERSION = 1;

    @JsonIgnoreProperties({"pendingEvents", "version"})
    abstract static class AggregateBaseMixin {}

    private final SnapshotStore store;
    private final EventStore events;
    private final Clock clock;
    private final JsonMapper mapper = JsonMapper.builder()
            .changeDefaultVisibility(v -> v.withVisibility(PropertyAccessor.ALL, Visibility.NONE)
                    .withVisibility(PropertyAccessor.FIELD, Visibility.ANY)
                    .withVisibility(PropertyAccessor.CREATOR, Visibility.ANY))
            .enable(tools.jackson.databind.MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .addMixIn(EventSourcedAggregate.class, AggregateBaseMixin.class)
            .build();

    AggregateSnapshots(SnapshotStore store, EventStore events, Clock clock) {
        this.store = store;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Carrega o agregado: restaura do snapshot (se houver e for legível) e aplica a cauda; senão replay completo.
     *
     * @param opener decifra o conteúdo (recebe o titular e o payload); {@code null} se não houver cifra
     * @param fullReplay reconstrói do histórico completo (usado quando não há snapshot utilizável)
     * @return o agregado, ou vazio se o stream não existir
     */
    public <E extends DomainEvent, A extends EventSourcedAggregate<E>> Optional<A> load(
            String stream,
            Class<A> type,
            BiFunction<String, byte[], byte[]> opener,
            java.util.function.Function<StoredEvent, E> decoder,
            Function<List<StoredEvent>, A> fullReplay) {
        var restored = restore(stream, type, opener, decoder);
        if (restored.isPresent()) {
            return restored;
        }
        var all = events.readStream(stream);
        return all.isEmpty() ? Optional.empty() : Optional.of(fullReplay.apply(all));
    }

    private <E extends DomainEvent, A extends EventSourcedAggregate<E>> Optional<A> restore(
            String stream,
            Class<A> type,
            BiFunction<String, byte[], byte[]> opener,
            Function<StoredEvent, E> decoder) {
        var snapshot = store.load(stream);
        if (snapshot.isEmpty() || snapshot.get().schemaVersion() != SCHEMA_VERSION) {
            return Optional.empty();
        }
        var snap = snapshot.get();
        var plain = opener == null ? snap.data() : opener.apply(snap.subject(), snap.data());
        A aggregate;
        try {
            aggregate = mapper.readValue(plain, type);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .addKeyValue("eventstore.stream", stream)
                    .setCause(ex)
                    .log("Snapshot ilegível; usando replay completo");
            return Optional.empty();
        }
        var tail = events.readStream(stream, snap.revision() + 1);
        var lastRevision = tail.isEmpty() ? snap.revision() : tail.getLast().revision();
        aggregate.resumeFrom(tail.stream().map(decoder).toList(), lastRevision);
        log.atDebug()
                .addKeyValue("eventstore.stream", stream)
                .addKeyValue("snapshot.revision", snap.revision())
                .addKeyValue("snapshot.tail", tail.size())
                .log("Agregado restaurado de snapshot");
        return Optional.of(aggregate);
    }

    /**
     * Tira snapshot se a gravação acabou de cruzar um múltiplo de {@link #INTERVAL} eventos. Best-effort: falhas são
     * registradas e não afetam o comando.
     *
     * @param previousVersion revisão do stream antes da gravação (-1 se novo)
     * @param newVersion revisão do último evento gravado
     */
    public void maybeSnapshot(
            String stream,
            EventSourcedAggregate<?> aggregate,
            long previousVersion,
            long newVersion,
            String subject,
            UnaryOperator<byte[]> sealer) {
        if (Math.floorDiv(newVersion + 1, INTERVAL) <= Math.floorDiv(previousVersion + 1, INTERVAL)) {
            return;
        }
        try {
            var plain = mapper.writeValueAsBytes(aggregate);
            var data = sealer == null ? plain : sealer.apply(plain);
            store.save(new Snapshot(stream, newVersion, SCHEMA_VERSION, subject, data, clock.instant()));
            log.atDebug()
                    .addKeyValue("eventstore.stream", stream)
                    .addKeyValue("snapshot.revision", newVersion)
                    .log("Snapshot gravado");
        } catch (RuntimeException ex) {
            log.atWarn()
                    .addKeyValue("eventstore.stream", stream)
                    .setCause(ex)
                    .log("Falha ao gravar snapshot; seguirá com replay completo");
        }
    }
}
