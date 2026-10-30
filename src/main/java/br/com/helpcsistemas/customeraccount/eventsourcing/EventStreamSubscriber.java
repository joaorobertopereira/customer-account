package br.com.helpcsistemas.customeraccount.eventsourcing;

/**
 * SPI para consumidores de eventos (projeções, process managers). Cada implementação registrada como bean ganha uma
 * catch-up subscription no {@code $all}, filtrada por prefixo de stream, com checkpoint persistido.
 *
 * <p>Entrega é <b>at-least-once</b>: {@link #onEvent} deve ser idempotente. Se lançar exceção, a subscription é
 * reiniciada a partir do último checkpoint.
 */
public interface EventStreamSubscriber {

    /** Nome estável: identifica o checkpoint. Alterar o nome reprocessa todo o histórico. */
    String subscriptionName();

    String streamPrefix();

    void onEvent(StoredEvent event);
}
