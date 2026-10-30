package br.com.helpcsistemas.customeraccount.verification.application.query;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Instant;

/**
 * Falha de provedor registrada para o tenant. Sem dados do titular.
 *
 * @param continuedWith provedor de contingência que assumiu o fluxo; nulo se não havia contingência
 */
public record ProviderIncidentView(
        String id,
        TenantId tenantId,
        VerificationId verificationId,
        VerificationType type,
        ProviderId provider,
        String errorCode,
        long latencyMs,
        ProviderId continuedWith,
        Instant occurredAt) {}
