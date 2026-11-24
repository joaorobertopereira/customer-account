package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.ParkedEvents;
import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class ParkedEventService implements ParkedEvents {

    private static final Logger log = LoggerFactory.getLogger(ParkedEventService.class);

    private final ParkedEventStore store;
    private final List<EventStreamSubscriber> subscribers;

    ParkedEventService(ParkedEventStore store, List<EventStreamSubscriber> subscribers) {
        this.store = store;
        this.subscribers = subscribers;
    }

    @Override
    public List<Parked> list() {
        return store.findAll().stream()
                .map(e -> new Parked(
                        e.id(),
                        e.subscription(),
                        e.event().stream(),
                        e.event().revision(),
                        e.event().eventId().toString(),
                        e.event().type(),
                        e.error(),
                        e.attempts(),
                        e.parkedAt()))
                .toList();
    }

    @Override
    public void reprocess(String id) {
        var entry = store.find(id).orElseThrow(this::notFound);
        var subscriber = subscribers.stream()
                .filter(s -> s.subscriptionName().equals(entry.subscription()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "SUBSCRIBER_NOT_FOUND", "Subscription inexistente: " + entry.subscription()));
        subscriber.onEvent(entry.event());
        store.remove(id);
        log.atInfo()
                .addKeyValue("subscription.name", entry.subscription())
                .addKeyValue("event.id", entry.event().eventId().toString())
                .log("Evento estacionado reprocessado com sucesso");
    }

    @Override
    public void discard(String id) {
        store.find(id).orElseThrow(this::notFound);
        store.remove(id);
        log.atWarn().addKeyValue("parked.id", id).log("Evento estacionado descartado");
    }

    private ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("PARKED_EVENT_NOT_FOUND", "Evento estacionado não encontrado");
    }
}
