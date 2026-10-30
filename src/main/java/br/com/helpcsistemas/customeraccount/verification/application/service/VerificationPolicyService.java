package br.com.helpcsistemas.customeraccount.verification.application.service;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ConfigureVerificationPolicyUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.QueryVerificationPolicyUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderDescriptor;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationPolicyRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProviderGateway;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationPolicyView;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class VerificationPolicyService implements ConfigureVerificationPolicyUseCase, QueryVerificationPolicyUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerificationPolicyService.class);

    private final VerificationPolicyRepositoryPort policies;
    private final VerificationProviderGateway providers;
    private final Clock clock;

    VerificationPolicyService(
            VerificationPolicyRepositoryPort policies, VerificationProviderGateway providers, Clock clock) {
        this.policies = policies;
        this.providers = providers;
        this.clock = clock;
    }

    @Override
    @Observed(name = "verification.policy", contextualName = "configure-verification-rule")
    public VerificationPolicyView configure(TenantId tenant, VerificationRule rule, String changedBy, String reason) {
        ensureAvailable(rule.primary(), rule);
        if (rule.hasFallback()) {
            ensureAvailable(rule.fallback(), rule);
        }
        var policy = policies.load(tenant);
        var previous = policy.ruleFor(rule.type());
        policy.configure(rule, changedBy, reason, clock);
        if (policy.hasPendingEvents()) {
            policies.save(policy);
            log.atInfo()
                    .addKeyValue("audit.action", "verification-policy.changed")
                    .addKeyValue("tenant.id", tenant)
                    .addKeyValue("verification.type", rule.type())
                    .addKeyValue(
                            "provider.previous",
                            previous.map(r -> r.primary().value()).orElse("none"))
                    .addKeyValue("provider.primary", rule.primary())
                    .addKeyValue(
                            "provider.fallback",
                            rule.hasFallback() ? rule.fallback().value() : "none")
                    .addKeyValue("policy.version", policy.version())
                    .addKeyValue("changed.by", changedBy)
                    .log("Política de validação alterada; vale a partir da próxima validação");
        }
        return VerificationPolicyView.of(policy);
    }

    @Override
    public VerificationPolicyView current(TenantId tenant) {
        return VerificationPolicyView.of(policies.load(tenant));
    }

    @Override
    public List<VerificationRuleConfigured> history(TenantId tenant) {
        return policies.history(tenant);
    }

    @Override
    public List<ProviderDescriptor> availableProviders() {
        return providers.providers();
    }

    private void ensureAvailable(ProviderId provider, VerificationRule rule) {
        if (!providers.supports(provider, rule.type())) {
            throw new InvalidValueException(
                    "PROVIDER_NOT_AVAILABLE",
                    "Provedor " + provider + " não está disponível para validação " + rule.type());
        }
    }
}
