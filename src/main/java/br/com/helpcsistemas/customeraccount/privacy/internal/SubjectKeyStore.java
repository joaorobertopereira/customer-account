package br.com.helpcsistemas.customeraccount.privacy.internal;

import java.time.Instant;
import java.util.Optional;

/** Armazena DEKs embrulhadas por titular. Apagar o registro = crypto-shredding. */
interface SubjectKeyStore {

    Optional<SubjectKey> find(String subjectId);

    /** @return false se já existir chave para o titular (outra instância venceu a corrida) */
    boolean insertIfAbsent(SubjectKey key);

    void delete(String subjectId);

    record SubjectKey(String subjectId, String wrappedKey, String provider, Instant createdAt) {}
}
