package br.com.helpcsistemas.customeraccount.privacy.internal;

/** Gera e desembrulha chaves de dados (DEK) usando uma chave mestra (KEK) que nunca sai do provedor. */
interface KeyEncryptionService {

    DataKey generateDataKey();

    byte[] unwrap(String wrappedKey);

    String providerName();

    /** @param wrapped DEK cifrada pela KEK (seguro para persistir) */
    record DataKey(byte[] plaintext, String wrapped) {}
}
