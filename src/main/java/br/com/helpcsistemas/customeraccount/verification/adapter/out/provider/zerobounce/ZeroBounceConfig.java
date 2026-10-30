package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.zerobounce;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderCredentialsResolver;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderHttpClients;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvider;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvidersProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.verification.providers.zerobounce.enabled", havingValue = "true")
class ZeroBounceConfig {

    @Bean
    VerificationProvider zeroBounceProvider(
            RestClient.Builder builder,
            ProviderCredentialsResolver credentials,
            JsonMapper json,
            VerificationProvidersProperties properties) {
        return new ZeroBounceProvider(ProviderHttpClients.create(builder, properties.zerobounce()), credentials, json);
    }
}
