package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.simulated;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvider;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.simulated.SimulatedVerificationProvider.Behavior;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Nunca habilite em produção ({@code app.verification.simulated-providers-enabled=false}). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.verification.simulated-providers-enabled", havingValue = "true")
class SimulatedProvidersConfig {

    @Bean
    VerificationProvider simulatedApproveProvider() {
        return new SimulatedVerificationProvider("simulated-approve", Behavior.APPROVE);
    }

    @Bean
    VerificationProvider simulatedRejectProvider() {
        return new SimulatedVerificationProvider("simulated-reject", Behavior.REJECT);
    }

    @Bean
    VerificationProvider simulatedInconclusiveProvider() {
        return new SimulatedVerificationProvider("simulated-inconclusive", Behavior.INCONCLUSIVE);
    }

    @Bean
    VerificationProvider simulatedUnavailableProvider() {
        return new SimulatedVerificationProvider("simulated-unavailable", Behavior.UNAVAILABLE);
    }
}
