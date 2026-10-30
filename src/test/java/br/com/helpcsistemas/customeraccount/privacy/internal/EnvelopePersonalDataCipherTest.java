package br.com.helpcsistemas.customeraccount.privacy.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class EnvelopePersonalDataCipherTest {

    private static final String MASTER_KEY = "ZGV2LWxvY2FsLW1hc3Rlci1rZXktbm90LTQtcHJvZCE=";

    private final InMemorySubjectKeyStore store = new InMemorySubjectKeyStore();
    private final SubjectKeyManager keys = new SubjectKeyManager(
            store,
            new LocalKeyEncryptionService(MASTER_KEY),
            Clock.systemUTC(),
            new PrivacyProperties(
                    PrivacyProperties.KeyProvider.LOCAL, "unused", MASTER_KEY, Duration.ofMinutes(5), 100, null));
    private final EnvelopePersonalDataCipher cipher = new EnvelopePersonalDataCipher(keys);
    private final byte[] plaintext = "{\"cpf\":\"52998224725\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void roundTripsWithRandomIv() {
        var first = cipher.encrypt("subject-1", plaintext);
        var second = cipher.encrypt("subject-1", plaintext);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt("subject-1", first)).isEqualTo(plaintext);
        assertThat(store.keys).containsOnlyKeys("subject-1");
        assertThat(store.keys.get("subject-1").wrappedKey()).startsWith("local:v1:");
    }

    @Test
    void ciphertextIsBoundToItsSubject() {
        var ciphertext = cipher.encrypt("subject-1", plaintext);
        cipher.encrypt("subject-2", plaintext);

        assertThatThrownBy(() -> cipher.decrypt("subject-2", ciphertext)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shreddingMakesDataUnrecoverable() {
        var ciphertext = cipher.encrypt("subject-1", plaintext);

        keys.shred("subject-1");

        assertThat(store.keys).isEmpty();
        assertThatThrownBy(() -> cipher.decrypt("subject-1", ciphertext))
                .isInstanceOf(PersonalDataErasedException.class);
    }

    @Test
    void keysSurviveCacheEvictionThroughTheStore() {
        var ciphertext = cipher.encrypt("subject-1", plaintext);
        var freshManager = new SubjectKeyManager(
                store,
                new LocalKeyEncryptionService(MASTER_KEY),
                Clock.systemUTC(),
                new PrivacyProperties(
                        PrivacyProperties.KeyProvider.LOCAL, "unused", MASTER_KEY, Duration.ofMinutes(5), 100, null));

        assertThat(new EnvelopePersonalDataCipher(freshManager).decrypt("subject-1", ciphertext))
                .isEqualTo(plaintext);
    }

    static final class InMemorySubjectKeyStore implements SubjectKeyStore {

        final Map<String, SubjectKey> keys = new ConcurrentHashMap<>();

        @Override
        public Optional<SubjectKey> find(String subjectId) {
            return Optional.ofNullable(keys.get(subjectId));
        }

        @Override
        public boolean insertIfAbsent(SubjectKey key) {
            return keys.putIfAbsent(key.subjectId(), key) == null;
        }

        @Override
        public void delete(String subjectId) {
            keys.remove(subjectId);
        }
    }
}
