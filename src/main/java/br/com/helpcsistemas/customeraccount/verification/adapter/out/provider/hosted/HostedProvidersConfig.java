package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.hosted;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.HostedCallbackProvider;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderCredentialsResolver;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderHttpClients;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvidersProperties.Http;
import br.com.helpcsistemas.customeraccount.verification.config.HostedProvidersProperties;
import br.com.helpcsistemas.customeraccount.verification.config.HostedProvidersProperties.Provider;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class HostedProvidersConfig {

    @Bean
    @ConditionalOnProperty(name = "app.verification.hosted-providers.legitimuz.enabled", havingValue = "true")
    HostedCallbackProvider legitimuzHostedProvider(
            RestClient.Builder builder,
            ProviderCredentialsResolver credentials,
            JsonMapper json,
            Clock clock,
            HostedProvidersProperties properties) {
        return create("legitimuz", builder, credentials, json, clock, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.verification.hosted-providers.netrin.enabled", havingValue = "true")
    HostedCallbackProvider netrinHostedProvider(
            RestClient.Builder builder,
            ProviderCredentialsResolver credentials,
            JsonMapper json,
            Clock clock,
            HostedProvidersProperties properties) {
        return create("netrin", builder, credentials, json, clock, properties);
    }

    private static HostedCallbackProvider create(
            String id,
            RestClient.Builder builder,
            ProviderCredentialsResolver credentials,
            JsonMapper json,
            Clock clock,
            HostedProvidersProperties properties) {
        Provider settings = properties.providers().get(id);
        return new GenericHostedProvider(
                ProviderId.of(id),
                settings,
                ProviderHttpClients.create(builder, new Http(true, settings.baseUrl(), settings.timeout())),
                credentials,
                json,
                clock);
    }
}
