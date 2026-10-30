package br.com.helpcsistemas.customeraccount.privacy.internal;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** KEK local em memória. Apenas para desenvolvimento e testes: em produção use o Vault Transit. */
class LocalKeyEncryptionService implements KeyEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(LocalKeyEncryptionService.class);
    private static final String PREFIX = "local:v1:";
    private static final byte[] AAD = "customer-account-dek".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey masterKey;

    LocalKeyEncryptionService(String base64MasterKey) {
        if (base64MasterKey == null || base64MasterKey.isBlank()) {
            throw new IllegalStateException("app.privacy.local-master-key é obrigatório com key-provider=local");
        }
        var raw = Base64.getDecoder().decode(base64MasterKey);
        if (raw.length != 32) {
            throw new IllegalStateException("app.privacy.local-master-key deve ter 32 bytes (AES-256)");
        }
        this.masterKey = new SecretKeySpec(raw, "AES");
        log.warn("KEK LOCAL em uso (app.privacy.key-provider=local). Proibido em produção.");
    }

    @Override
    public DataKey generateDataKey() {
        var dek = new byte[32];
        RANDOM.nextBytes(dek);
        var wrapped = PREFIX + Base64.getEncoder().encodeToString(AesGcm.encrypt(masterKey, dek, AAD));
        return new DataKey(dek, wrapped);
    }

    @Override
    public byte[] unwrap(String wrappedKey) {
        if (!wrappedKey.startsWith(PREFIX)) {
            throw new IllegalStateException("DEK não foi gerada pelo provedor local");
        }
        return AesGcm.decrypt(masterKey, Base64.getDecoder().decode(wrappedKey.substring(PREFIX.length())), AAD);
    }

    @Override
    public String providerName() {
        return "local";
    }
}
