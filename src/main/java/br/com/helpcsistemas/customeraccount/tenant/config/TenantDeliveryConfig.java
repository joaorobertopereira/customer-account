package br.com.helpcsistemas.customeraccount.tenant.config;

import br.com.helpcsistemas.customeraccount.tenant.domain.model.DeliveryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TenantDeliveryConfig {

    @Bean
    DeliveryPolicy deliveryPolicy(TenantWebhookProperties properties) {
        return new DeliveryPolicy(properties.maxAttempts());
    }
}
