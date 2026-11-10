package br.com.helpcsistemas.customeraccount;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Subscriber de teste (prefixo exclusivo {@code poisontest-}) que falha em eventos {@code Bad.v1} até ser "consertado".
 * Vive no contexto compartilhado dos testes de integração: um segundo contexto duplicaria as subscriptions de produção.
 */
@Component
class PoisonTestSubscriber implements EventStreamSubscriber {

    static final AtomicBoolean FIXED = new AtomicBoolean(false);
    static final List<String> PROCESSED = new CopyOnWriteArrayList<>();

    @Override
    public String subscriptionName() {
        return "poison-test-v1";
    }

    @Override
    public String streamPrefix() {
        return "poisontest-";
    }

    @Override
    public void onEvent(StoredEvent event) {
        if ("Bad.v1".equals(event.type()) && !FIXED.get()) {
            throw new IllegalStateException("evento envenenado");
        }
        PROCESSED.add(event.type());
    }
}
