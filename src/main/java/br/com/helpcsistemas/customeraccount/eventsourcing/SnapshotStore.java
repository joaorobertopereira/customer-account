package br.com.helpcsistemas.customeraccount.eventsourcing;

import java.util.Optional;

/** Porta de persistência de snapshots (otimização de leitura; a fonte da verdade continua sendo o stream). */
public interface SnapshotStore {

    Optional<Snapshot> load(String stream);

    /** Grava apenas se a revisão for mais nova que a já armazenada (idempotente sob concorrência). */
    void save(Snapshot snapshot);
}
