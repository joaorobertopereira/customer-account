package br.com.helpcsistemas.customeraccount.secrets.internal;

import br.com.helpcsistemas.customeraccount.secrets.SecretStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class SecretsConfig {

    @Bean
    SecretStore secretStore(SecretsProperties properties, RestClient.Builder builder) {
        return switch (properties.provider()) {
            case VAULT -> new VaultKvSecretStore(builder, properties.vault(), properties.cacheTtl());
            case LOCAL -> new LocalSecretStore(properties.local());
        };
    }
}
