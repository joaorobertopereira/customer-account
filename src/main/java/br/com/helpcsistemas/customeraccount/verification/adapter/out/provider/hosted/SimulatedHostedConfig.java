package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.hosted;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.verification.simulated-providers-enabled", havingValue = "true")
class SimulatedHostedConfig {

    @Bean
    SimulatedHostedProvider simulatedHostedProvider(
            @Value("${app.simulated-hosted.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.simulated-hosted.secret:local-only-not-for-production}") String secret,
            JsonMapper json,
            Clock clock) {
        return new SimulatedHostedProvider(baseUrl, secret, json, clock);
    }
}
