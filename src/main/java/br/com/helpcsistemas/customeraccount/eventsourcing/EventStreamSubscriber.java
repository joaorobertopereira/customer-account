package br.com.helpcsistemas.customeraccount.eventsourcing;

import java.util.Optional;

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

    /**
     * Projeções (read models) podem ser reconstruídas do zero: {@link #resetProjection()} limpa o estado derivado e a
     * subscription reprocessa todo o histórico. Subscribers com efeitos externos (process managers) não são.
     */
    default boolean rebuildable() {
        return false;
    }

    /** Limpa o estado derivado desta projeção. Só é chamado se {@link #rebuildable()} for verdadeiro. */
    default void resetProjection() {}

    /**
     * Subscription de onde herdar o checkpoint na primeira execução. Serve para extrair um efeito externo de uma
     * subscription existente sem reprocessar o histórico: sem checkpoint próprio, a nova subscription começa da
     * posição da origem (e o grava como seu). Sem checkpoint na origem, começa do início como qualquer outra.
     */
    default Optional<String> inheritsCheckpointFrom() {
        return Optional.empty();
    }
}
