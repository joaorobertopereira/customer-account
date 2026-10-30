package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.Position;
import io.kurrent.dbclient.ResolvedEvent;
import io.kurrent.dbclient.SubscribeToAllOptions;
import io.kurrent.dbclient.Subscription;
import io.kurrent.dbclient.SubscriptionFilter;
import io.kurrent.dbclient.SubscriptionListener;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Mantém uma catch-up subscription no {@code $all} para cada {@link EventStreamSubscriber}. Ordem por stream é
 * garantida; o checkpoint é salvo após cada evento processado com sucesso. Em falha, a subscription é parada e
 * recriada a partir do último checkpoint (retry com atraso fixo).
 */
@Component
class EventStreamSubscriptionRunner implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EventStreamSubscriptionRunner.class);

    private final KurrentDBClient client;
    private final List<EventStreamSubscriber> subscribers;
    private final SubscriptionCheckpointStore checkpoints;
    private final RecordedEventMapper mapper;
    private final TraceContextPropagation tracing;
    private final EventStoreProperties properties;
    private final Map<String, Subscription> active = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("eventstore-subscriptions-", 0).factory());
    private volatile boolean running;

    EventStreamSubscriptionRunner(
            KurrentDBClient client,
            List<EventStreamSubscriber> subscribers,
            SubscriptionCheckpointStore checkpoints,
            RecordedEventMapper mapper,
            TraceContextPropagation tracing,
            EventStoreProperties properties) {
        this.client = client;
        this.subscribers = subscribers;
        this.checkpoints = checkpoints;
        this.mapper = mapper;
        this.tracing = tracing;
        this.properties = properties;
    }

    @Override
    public void start() {
        running = true;
        if (!properties.subscriptionsEnabled()) {
            log.info(
                    "Subscriptions do event store desabilitadas nesta instância (app.eventstore.subscriptions-enabled)");
            return;
        }
        subscribers.forEach(subscriber -> scheduler.execute(() -> subscribe(subscriber)));
    }

    @Override
    public void stop() {
        running = false;
        active.values().forEach(Subscription::stop);
        active.clear();
        scheduler.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void subscribe(EventStreamSubscriber subscriber) {
        if (!running) {
            return;
        }
        var name = subscriber.subscriptionName();
        try {
            var checkpoint = checkpoints.load(name);
            var options = SubscribeToAllOptions.get()
                    .filter(SubscriptionFilter.newBuilder()
                            .addStreamNamePrefix(subscriber.streamPrefix())
                            .build());
            checkpoint.ifPresentOrElse(options::fromPosition, options::fromStart);

            var subscription = client.subscribeToAll(new Listener(subscriber), options)
                    .get(properties.operationTimeout().toMillis(), TimeUnit.MILLISECONDS);
            active.put(name, subscription);
            log.atInfo()
                    .addKeyValue("subscription.name", name)
                    .addKeyValue(
                            "subscription.from",
                            checkpoint.map(Position::toString).orElse("start"))
                    .log("Subscription iniciada");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.atWarn()
                    .addKeyValue("subscription.name", name)
                    .setCause(ex)
                    .log("Falha ao iniciar subscription; nova tentativa agendada");
            scheduleResubscribe(subscriber);
        }
    }

    private void scheduleResubscribe(EventStreamSubscriber subscriber) {
        active.remove(subscriber.subscriptionName());
        if (running) {
            scheduler.schedule(
                    () -> subscribe(subscriber), properties.resubscribeDelay().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private final class Listener extends SubscriptionListener {

        private final EventStreamSubscriber subscriber;
        private final String name;
        // Após uma falha, eventos já bufferizados não podem ser processados (quebraria a ordem/idempotência).
        private volatile boolean failed;

        Listener(EventStreamSubscriber subscriber) {
            this.subscriber = subscriber;
            this.name = subscriber.subscriptionName();
        }

        @Override
        public void onEvent(Subscription subscription, ResolvedEvent resolved) {
            var recorded = resolved.getOriginalEvent();
            if (failed || recorded.getEventType().startsWith("$")) {
                return;
            }
            var event = mapper.toStored(recorded);
            var tags = Map.of("subscription.name", name, "event.type", event.type());
            try {
                tracing.runInConsumerSpan("subscription " + name, event.metadata(), tags, () -> {
                    subscriber.onEvent(event);
                    checkpoints.save(name, recorded.getPosition());
                    log.atDebug()
                            .addKeyValue("subscription.name", name)
                            .addKeyValue("event.type", event.type())
                            .addKeyValue("event.revision", event.revision())
                            .log("Evento processado pela subscription");
                });
            } catch (RuntimeException ex) {
                failed = true;
                log.atError()
                        .addKeyValue("subscription.name", name)
                        .addKeyValue("event.type", event.type())
                        .addKeyValue("event.id", event.eventId().toString())
                        .setCause(ex)
                        .log("Falha ao processar evento; subscription será reiniciada do último checkpoint");
                subscription.stop();
                scheduleResubscribe(subscriber);
            }
        }

        @Override
        public void onCaughtUp(Subscription subscription, Instant timestamp, Long streamRevision, Position position) {
            log.atInfo().addKeyValue("subscription.name", name).log("Subscription em dia (modo live)");
        }

        @Override
        public void onCancelled(Subscription subscription, Throwable exception) {
            if (exception == null || failed) {
                return;
            }
            log.atWarn()
                    .addKeyValue("subscription.name", name)
                    .setCause(exception)
                    .log("Subscription derrubada; nova tentativa agendada");
            scheduleResubscribe(subscriber);
        }
    }
}
