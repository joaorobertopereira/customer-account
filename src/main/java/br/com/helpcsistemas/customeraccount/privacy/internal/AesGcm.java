package br.com.helpcsistemas.customeraccount.privacy.internal;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-256-GCM com IV aleatório de 96 bits por operação. Saída: {@code iv || ciphertext+tag}. */
final class AesGcm {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcm() {}

    static byte[] encrypt(SecretKey key, byte[] plaintext, byte[] aad) {
        try {
            var iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            var ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(IV_LENGTH + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao cifrar", ex);
        }
    }

    static byte[] decrypt(SecretKey key, byte[] payload, byte[] aad) {
        if (payload.length <= IV_LENGTH) {
            throw new IllegalStateException("Payload cifrado inválido");
        }
        try {
            var cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
            cipher.updateAAD(aad);
            return cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
        } catch (GeneralSecurityException ex) {
            // Tag inválida = dado adulterado ou chave errada: nunca devolver conteúdo parcial.
            throw new IllegalStateException("Falha de integridade ao decifrar", ex);
        }
    }
}
