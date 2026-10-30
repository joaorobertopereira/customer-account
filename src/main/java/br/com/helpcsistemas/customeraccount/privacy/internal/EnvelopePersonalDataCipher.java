package br.com.helpcsistemas.customeraccount.privacy.internal;

import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Formato: {@code [versão=1][iv(12)][ciphertext+tag]}. O subjectId é usado como AAD: um ciphertext não pode ser
 * "transplantado" para outro titular.
 */
class EnvelopePersonalDataCipher implements PersonalDataCipher {

    private static final byte FORMAT_V1 = 1;

    private final SubjectKeyManager keys;

    EnvelopePersonalDataCipher(SubjectKeyManager keys) {
        this.keys = keys;
    }

    @Override
    public byte[] encrypt(String subjectId, byte[] plaintext) {
        var payload = AesGcm.encrypt(keys.getOrCreate(subjectId), plaintext, aad(subjectId));
        return ByteBuffer.allocate(1 + payload.length)
                .put(FORMAT_V1)
                .put(payload)
                .array();
    }

    @Override
    public byte[] decrypt(String subjectId, byte[] ciphertext) {
        if (ciphertext.length < 1 || ciphertext[0] != FORMAT_V1) {
            throw new IllegalStateException("Formato de dado cifrado não suportado");
        }
        var key = keys.find(subjectId)
                .orElseThrow(() -> new PersonalDataErasedException("Dados do titular foram eliminados"));
        var payload = new byte[ciphertext.length - 1];
        System.arraycopy(ciphertext, 1, payload, 0, payload.length);
        return AesGcm.decrypt(key, payload, aad(subjectId));
    }

    private static byte[] aad(String subjectId) {
        return subjectId.getBytes(StandardCharsets.UTF_8);
    }
}
