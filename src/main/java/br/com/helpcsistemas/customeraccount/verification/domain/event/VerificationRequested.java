package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;
import java.time.Instant;
import java.util.UUID;

/** @param rule cópia da regra vigente: alterações posteriores da política não afetam esta validação */
public record VerificationRequested(
        VerificationId verificationId,
        TenantId tenantId,
        UUID customerId,
        VerificationRule rule,
        long policyVersion,
        String requestedBy,
        Instant occurredAt)
        implements VerificationEvent {}
