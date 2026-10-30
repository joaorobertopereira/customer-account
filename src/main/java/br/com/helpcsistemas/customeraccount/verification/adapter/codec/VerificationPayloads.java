package br.com.helpcsistemas.customeraccount.verification.adapter.codec;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.InconclusiveAction;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationOutcome;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Esquemas persistidos (v1), desacoplados das classes de domínio. */
final class VerificationPayloads {

    private VerificationPayloads() {}

    record RuleV1(
            VerificationType type,
            String primary,
            String fallback,
            Integer minimumScore,
            InconclusiveAction onInconclusive,
            boolean enabled) {

        static RuleV1 from(VerificationRule rule) {
            return rule == null
                    ? null
                    : new RuleV1(
                            rule.type(),
                            rule.primary().value(),
                            rule.hasFallback() ? rule.fallback().value() : null,
                            rule.minimumScore(),
                            rule.onInconclusive(),
                            rule.enabled());
        }

        static VerificationRule toDomain(RuleV1 rule) {
            return rule == null
                    ? null
                    : new VerificationRule(
                            rule.type(),
                            new ProviderId(rule.primary()),
                            rule.fallback() == null ? null : new ProviderId(rule.fallback()),
                            rule.minimumScore(),
                            rule.onInconclusive(),
                            rule.enabled());
        }
    }

    record RuleConfiguredV1(
            String tenantId, RuleV1 rule, RuleV1 previous, String changedBy, String reason, Instant occurredAt) {}

    record RequestedV1(
            UUID verificationId,
            String tenantId,
            UUID customerId,
            RuleV1 rule,
            long policyVersion,
            String requestedBy,
            Instant occurredAt) {

        TenantId tenant() {
            return TenantId.of(tenantId);
        }
    }

    record SubmittedV1(UUID verificationId, String provider, int attempt, Instant occurredAt) {}

    record CallFailedV1(UUID verificationId, String provider, String errorCode, long latencyMs, Instant occurredAt) {}

    record FailedOverV1(UUID verificationId, String from, String to, Instant occurredAt) {}

    record ResponseReceivedV1(
            UUID verificationId,
            String provider,
            ProviderDecision decision,
            Integer score,
            List<String> reasonCodes,
            String transactionId,
            long latencyMs,
            String rawResponse,
            Instant occurredAt) {}

    record ConcludedV1(
            UUID verificationId,
            VerificationOutcome outcome,
            String provider,
            Integer score,
            List<String> reasonCodes,
            String providerTransactionId,
            Instant occurredAt) {}

    record ChallengeIssuedV1(
            UUID verificationId, String provider, String transactionId, Instant expiresAt, Instant occurredAt) {}

    record ChallengeRejectedV1(UUID verificationId, int attempt, Instant occurredAt) {}

    record DeliveredV1(UUID verificationId, int httpStatus, int attempts, Instant occurredAt) {}

    record DeliveryFailedV1(UUID verificationId, int attempts, Integer lastHttpStatus, Instant occurredAt) {}
}
