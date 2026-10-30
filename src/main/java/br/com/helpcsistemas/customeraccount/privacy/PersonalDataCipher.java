package br.com.helpcsistemas.customeraccount.privacy;

/** Cifra/decifra dados pessoais com a chave exclusiva do titular (AES-256-GCM, autenticado). */
public interface PersonalDataCipher {

    byte[] encrypt(String subjectId, byte[] plaintext);

    /** @throws PersonalDataErasedException se a chave do titular foi destruída (crypto-shredding) */
    byte[] decrypt(String subjectId, byte[] ciphertext);
}
