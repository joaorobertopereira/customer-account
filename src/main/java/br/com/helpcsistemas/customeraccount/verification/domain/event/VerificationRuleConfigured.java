package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;
import java.time.Instant;

/**
 * Regra criada ou alterada pelo administrador do tenant.
 *
 * @param previous regra anterior para o mesmo tipo (nula na primeira configuração)
 */
public record VerificationRuleConfigured(
        TenantId tenantId,
        VerificationRule rule,
        VerificationRule previous,
        String changedBy,
        String reason,
        Instant occurredAt)
        implements VerificationPolicyEvent {}
