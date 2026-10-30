package br.com.helpcsistemas.customeraccount.shared.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Base de agregados event-sourced: o estado é derivado exclusivamente dos eventos. Comandos validam invariantes e
 * chamam {@link #raise}; {@link #apply} só muta estado (sem validação), pois também é usado no replay do histórico.
 */
public abstract class EventSourcedAggregate<E extends DomainEvent> {

    /** Revisão do último evento persistido no stream; -1 indica agregado novo (stream inexistente). */
    public static final long NEW = -1;

    private final List<E> pendingEvents = new ArrayList<>();
    private long version = NEW;

    protected abstract void apply(E event);

    protected final void raise(E event) {
        apply(event);
        pendingEvents.add(event);
    }

    protected final void replay(List<? extends E> history, long version) {
        history.forEach(this::apply);
        this.version = version;
    }

    public final long version() {
        return version;
    }

    public final List<E> pendingEvents() {
        return List.copyOf(pendingEvents);
    }

    public final boolean hasPendingEvents() {
        return !pendingEvents.isEmpty();
    }

    public final void markCommitted(long newVersion) {
        pendingEvents.clear();
        this.version = newVersion;
    }
}
