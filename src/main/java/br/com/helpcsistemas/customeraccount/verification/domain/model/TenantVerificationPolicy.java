package br.com.helpcsistemas.customeraccount.verification.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.EventSourcedAggregate;
import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationPolicyEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuração de validação do tenant (um stream por tenant). A versão do agregado identifica a política vigente e é
 * gravada em cada validação, permitindo saber exatamente qual configuração foi aplicada.
 */
public class TenantVerificationPolicy extends EventSourcedAggregate<VerificationPolicyEvent> {

    private static final int MAX_REASON_LENGTH = 200;

    private final TenantId tenantId;
    private final Map<VerificationType, VerificationRule> rules = new EnumMap<>(VerificationType.class);

    private TenantVerificationPolicy(TenantId tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    public static TenantVerificationPolicy empty(TenantId tenantId) {
        return new TenantVerificationPolicy(tenantId);
    }

    public static TenantVerificationPolicy rehydrate(
            TenantId tenantId, List<VerificationPolicyEvent> history, long version) {
        var policy = new TenantVerificationPolicy(tenantId);
        policy.replay(history, version);
        return policy;
    }

    /** O motivo é obrigatório: alterações de provedor precisam ser justificáveis em auditoria. */
    public void configure(VerificationRule rule, String changedBy, String reason, Clock clock) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(changedBy, "changedBy");
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new InvalidValueException(
                    "INVALID_CHANGE_REASON",
                    "Informe o motivo da alteração (até " + MAX_REASON_LENGTH + " caracteres)");
        }
        var previous = rules.get(rule.type());
        if (!rule.equals(previous)) {
            raise(new VerificationRuleConfigured(tenantId, rule, previous, changedBy, reason.strip(), clock.instant()));
        }
    }

    @Override
    protected void apply(VerificationPolicyEvent event) {
        switch (event) {
            case VerificationRuleConfigured e -> rules.put(e.rule().type(), e.rule());
        }
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public Optional<VerificationRule> ruleFor(VerificationType type) {
        return Optional.ofNullable(rules.get(type));
    }

    public Map<VerificationType, VerificationRule> rules() {
        return Map.copyOf(rules);
    }
}
