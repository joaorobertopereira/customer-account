package br.com.helpcsistemas.customeraccount.privacy;

/**
 * Destrói a chave do titular: todo dado cifrado com ela (eventos imutáveis, read models, backups) torna-se
 * irrecuperável. Operação idempotente e irreversível.
 */
public interface CryptoShredder {

    void shred(String subjectId);
}
