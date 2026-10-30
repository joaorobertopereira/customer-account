package br.com.helpcsistemas.customeraccount.privacy.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ciclo de vida das DEKs por titular. Mantém DEKs desembrulhadas em cache curto (evita uma chamada ao Vault por
 * operação). Trade-off: após o shredding, outras instâncias podem manter a DEK em memória até o TTL expirar.
 */
class SubjectKeyManager {

    private static final Logger log = LoggerFactory.getLogger(SubjectKeyManager.class);

    private final SubjectKeyStore store;
    private final KeyEncryptionService kek;
    private final Clock clock;
    private final Cache<String, SecretKey> cache;

    SubjectKeyManager(SubjectKeyStore store, KeyEncryptionService kek, Clock clock, PrivacyProperties properties) {
        this.store = store;
        this.kek = kek;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.dataKeyCacheTtl())
                .maximumSize(properties.dataKeyCacheMaxSize())
                .build();
    }

    SecretKey getOrCreate(String subjectId) {
        return cache.get(subjectId, id -> load(id).orElseGet(() -> create(id)));
    }

    Optional<SecretKey> find(String subjectId) {
        var cached = cache.getIfPresent(subjectId);
        if (cached != null) {
            return Optional.of(cached);
        }
        var key = load(subjectId);
        key.ifPresent(k -> cache.put(subjectId, k));
        return key;
    }

    void shred(String subjectId) {
        store.delete(subjectId);
        cache.invalidate(subjectId);
        log.atInfo().addKeyValue("privacy.subjectId", subjectId).log("Chave do titular destruída (crypto-shredding)");
    }

    private Optional<SecretKey> load(String subjectId) {
        return store.find(subjectId).map(stored -> toKey(kek.unwrap(stored.wrappedKey())));
    }

    private SecretKey create(String subjectId) {
        var dataKey = kek.generateDataKey();
        var inserted = store.insertIfAbsent(
                new SubjectKeyStore.SubjectKey(subjectId, dataKey.wrapped(), kek.providerName(), clock.instant()));
        if (!inserted) {
            return load(subjectId).orElseThrow(() -> new IllegalStateException("DEK concorrente não encontrada"));
        }
        log.atDebug().addKeyValue("privacy.subjectId", subjectId).log("DEK criada para o titular");
        return toKey(dataKey.plaintext());
    }

    private static SecretKey toKey(byte[] raw) {
        return new SecretKeySpec(raw, "AES");
    }
}
