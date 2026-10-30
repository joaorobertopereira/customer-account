package br.com.helpcsistemas.customeraccount.eventsourcing;

import java.util.List;
import java.util.Optional;

/** Porta de acesso ao event store (fonte da verdade do lado de escrita). */
public interface EventStore {

    /**
     * Grava eventos atomicamente no stream, com controle otimista de concorrência.
     *
     * @return revisão do último evento gravado
     * @throws StreamRevisionConflictException se o stream não estiver na revisão esperada
     */
    long append(String stream, ExpectedRevision expected, List<NewEvent> events);

    /** Lê todos os eventos do stream em ordem; lista vazia se o stream não existir. */
    List<StoredEvent> readStream(String stream);

    Optional<StoredEvent> readLastEvent(String stream);
}
