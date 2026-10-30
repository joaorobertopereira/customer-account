package br.com.helpcsistemas.customeraccount.verification.adapter.codec;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultRules;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultStatus;
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

    record RuleV2(
            VerificationType type,
            String primary,
            String fallback,
            Integer minimumScore,
            InconclusiveAction onInconclusive,
            boolean enabled,
            HostedResultRules hostedResultRules) {

        static RuleV2 from(VerificationRule rule) {
            return rule == null
                    ? null
                    : new RuleV2(
                            rule.type(),
                            rule.primary().value(),
                            rule.hasFallback() ? rule.fallback().value() : null,
                            rule.minimumScore(),
                            rule.onInconclusive(),
                            rule.enabled(),
                            rule.hostedResultRules());
        }

        static VerificationRule toDomain(RuleV2 rule) {
            return rule == null
                    ? null
                    : new VerificationRule(
                            rule.type(),
                            new ProviderId(rule.primary()),
                            rule.fallback() == null ? null : new ProviderId(rule.fallback()),
                            rule.minimumScore(),
                            rule.onInconclusive(),
                            rule.enabled(),
                            rule.hostedResultRules());
        }
    }

    record RuleConfiguredV1(
            String tenantId, RuleV1 rule, RuleV1 previous, String changedBy, String reason, Instant occurredAt) {}

    record RuleConfiguredV2(
            String tenantId, RuleV2 rule, RuleV2 previous, String changedBy, String reason, Instant occurredAt) {}

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

    record RequestedV2(
            UUID verificationId,
            String tenantId,
            UUID customerId,
            RuleV2 rule,
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

    record ConcludedV2(
            UUID verificationId,
            VerificationOutcome outcome,
            String provider,
            Integer score,
            List<String> reasonCodes,
            String providerTransactionId,
            Instant validUntil,
            Instant occurredAt) {}

    record LinkIssuedV1(
            UUID verificationId,
            String provider,
            String sessionId,
            String url,
            Instant expiresAt,
            List<String> sharedFields,
            Instant occurredAt) {}

    record HostedResultV1(
            UUID verificationId,
            String provider,
            String sessionId,
            HostedResultStatus status,
            Integer score,
            List<String> reasonCodes,
            List<String> fraudSignals,
            String providerEventId,
            Instant resultOccurredAt,
            String rawResponse,
            Instant occurredAt) {}

    record LateResultIgnoredV1(
            UUID verificationId, String provider, String providerEventId, String reason, Instant occurredAt) {}

    record ValidityExpiredV1(UUID verificationId, Instant validUntil, Instant occurredAt) {}

    record ChallengeIssuedV1(
            UUID verificationId, String provider, String transactionId, Instant expiresAt, Instant occurredAt) {}

    record ChallengeRejectedV1(UUID verificationId, int attempt, Instant occurredAt) {}

    record DeliveredV1(UUID verificationId, int httpStatus, int attempts, Instant occurredAt) {}

    record DeliveryFailedV1(UUID verificationId, int attempts, Integer lastHttpStatus, Instant occurredAt) {}
}
