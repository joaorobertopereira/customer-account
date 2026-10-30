package br.com.helpcsistemas.customeraccount.verification.application.query;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderFailure;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationOutcome;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resultado padronizado devolvido ao tenant (igual para qualquer provedor). Inclui as falhas técnicas de provedores,
 * mesmo quando a contingência concluiu a validação. A resposta bruta do provedor não faz parte deste modelo.
 */
public record VerificationView(
        VerificationId id,
        TenantId tenantId,
        UUID customerId,
        VerificationType type,
        VerificationStatus status,
        VerificationOutcome outcome,
        ProviderId provider,
        Integer score,
        List<String> reasonCodes,
        String providerTransactionId,
        long policyVersion,
        boolean failedOver,
        List<ProviderFailure> failures,
        Instant requestedAt,
        Instant concludedAt,
        Instant challengeExpiresAt,
        int challengeAttempts,
        Instant deliveredAt,
        boolean deliveryFailed,
        String hostedLink,
        Instant hostedLinkExpiresAt,
        Instant validUntil,
        boolean validityExpired,
        boolean reused,
        long version) {

    public VerificationView {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public static VerificationView of(VerificationProcess process) {
        return new VerificationView(
                process.id(),
                process.tenantId(),
                process.customerId(),
                process.rule().type(),
                process.status(),
                process.outcome(),
                process.currentProvider(),
                process.score(),
                process.reasonCodes(),
                process.providerTransactionId(),
                process.policyVersion(),
                process.failedOver(),
                process.failures(),
                process.requestedAt(),
                process.concludedAt(),
                process.challengeExpiresAt(),
                process.challengeAttempts(),
                process.deliveredAt(),
                process.deliveryFailed(),
                process.status() == VerificationStatus.AWAITING_HOLDER ? process.hostedLink() : null,
                process.hostedLinkExpiresAt(),
                process.validUntil(),
                process.isValidityExpired(),
                false,
                process.version());
    }

    public VerificationView asReused() {
        return new VerificationView(
                id,
                tenantId,
                customerId,
                type,
                status,
                outcome,
                provider,
                score,
                reasonCodes,
                providerTransactionId,
                policyVersion,
                failedOver,
                failures,
                requestedAt,
                concludedAt,
                challengeExpiresAt,
                challengeAttempts,
                deliveredAt,
                deliveryFailed,
                hostedLink,
                hostedLinkExpiresAt,
                validUntil,
                validityExpired,
                true,
                version);
    }
}
