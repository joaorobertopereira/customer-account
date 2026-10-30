package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.twilio;

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
class TwilioConfig {

    @Bean
    @ConditionalOnProperty(name = "app.verification.providers.twilio-verify.enabled", havingValue = "true")
    VerificationProvider twilioVerifyProvider(
            RestClient.Builder builder,
            ProviderCredentialsResolver credentials,
            JsonMapper json,
            VerificationProvidersProperties properties) {
        return new TwilioVerifyProvider(
                ProviderHttpClients.create(builder, properties.twilioVerify()), credentials, json);
    }

    @Bean
    @ConditionalOnProperty(name = "app.verification.providers.twilio-lookup.enabled", havingValue = "true")
    VerificationProvider twilioLookupProvider(
            RestClient.Builder builder,
            ProviderCredentialsResolver credentials,
            JsonMapper json,
            VerificationProvidersProperties properties) {
        return new TwilioLookupProvider(
                ProviderHttpClients.create(builder, properties.twilioLookup()), credentials, json);
    }
}
