package br.com.helpcsistemas.customeraccount.privacy.internal;

import br.com.helpcsistemas.customeraccount.privacy.BlindIndex;
import br.com.helpcsistemas.customeraccount.privacy.CryptoShredder;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class PrivacyConfig {

    private static final String KEY_PROVIDER = "app.privacy.key-provider";

    @Bean
    @ConditionalOnProperty(name = KEY_PROVIDER, havingValue = "vault")
    KeyEncryptionService vaultKeyEncryptionService(RestClient.Builder builder, PrivacyProperties properties) {
        return new VaultTransitKeyEncryptionService(builder, properties.vault());
    }

    @Bean
    @ConditionalOnProperty(name = KEY_PROVIDER, havingValue = "local")
    KeyEncryptionService localKeyEncryptionService(PrivacyProperties properties) {
        return new LocalKeyEncryptionService(properties.localMasterKey());
    }

    @Bean
    SubjectKeyManager subjectKeyManager(
            SubjectKeyStore store, KeyEncryptionService kek, Clock clock, PrivacyProperties properties) {
        return new SubjectKeyManager(store, kek, clock, properties);
    }

    @Bean
    PersonalDataCipher personalDataCipher(SubjectKeyManager keys) {
        return new EnvelopePersonalDataCipher(keys);
    }

    @Bean
    CryptoShredder cryptoShredder(SubjectKeyManager keys) {
        return keys::shred;
    }

    @Bean
    BlindIndex blindIndex(PrivacyProperties properties) {
        return new HmacBlindIndex(properties.blindIndexKey());
    }
}
