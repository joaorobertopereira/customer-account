package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultRules;
import br.com.helpcsistemas.customeraccount.verification.domain.model.InconclusiveAction;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationOutcome;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class VerificationDtos {

    private VerificationDtos() {}

    record RequestVerificationRequest(
            @NotNull UUID customerId, @NotNull VerificationType type, URI returnUrl) {}

    /** Resultado padronizado: mesmo formato para qualquer provedor. */
    record VerificationResponse(
            String id,
            UUID customerId,
            VerificationType type,
            VerificationStatus status,
            VerificationOutcome outcome,
            String provider,
            Integer score,
            List<String> reasonCodes,
            String providerTransactionId,
            long policyVersion,
            FailoverResponse failover,
            ChallengeResponse challenge,
            DeliveryResponse delivery,
            HostedLinkResponse link,
            Instant validUntil,
            boolean reused,
            Instant requestedAt,
            Instant concludedAt) {}

    /** Presente quando a validação depende de resposta do titular (ex.: código OTP). */
    record ChallengeResponse(Instant expiresAt, int attempts, int remainingAttempts) {}

    /** Entrega ao webhook do tenant (nulo enquanto não houver desfecho). */
    record DeliveryResponse(Instant deliveredAt, boolean failed) {}

    record HostedLinkResponse(String url, Instant expiresAt) {}

    record ConfirmVerificationRequest(
            @NotBlank @Pattern(regexp = "\\d{4,10}") String code) {

        @Override
        public String toString() {
            return "ConfirmVerificationRequest[***]";
        }
    }

    /** Falhas técnicas de provedores ocorridas nesta validação (o fluxo seguiu pela contingência, se configurada). */
    record FailoverResponse(boolean occurred, List<ProviderFailureResponse> failures) {}

    record ProviderFailureResponse(String provider, String errorCode, long latencyMs, Instant occurredAt) {}

    record ConfigureRuleRequest(
            @NotBlank @Size(max = 40) String primaryProvider,
            @Size(max = 40) String fallbackProvider,
            @Min(0) @Max(100) Integer minimumScore,
            @NotNull InconclusiveAction onInconclusive,
            @NotNull Boolean enabled,
            @NotBlank @Size(max = 200) String reason,
            HostedResultRules.Action scoreBelowAction,
            @Size(max = 100) List<String> fraudReasonCodes,
            @Size(max = 100) List<String> manualReviewReasonCodes,
            HostedResultRules.Action onProviderError,
            @Min(1) @Max(3650) Integer validityDays,
            @Size(max = 20) List<String> allowedReturnUrlPrefixes) {}

    record RuleResponse(
            VerificationType type,
            String primaryProvider,
            String fallbackProvider,
            Integer minimumScore,
            InconclusiveAction onInconclusive,
            boolean enabled,
            HostedResultRules hostedResultRules) {}

    record PolicyResponse(String tenantId, long version, List<RuleResponse> rules) {}

    record PolicyChangeResponse(
            VerificationType type,
            RuleResponse previous,
            RuleResponse current,
            String changedBy,
            String reason,
            Instant changedAt) {}

    record ProviderResponse(String id, Set<VerificationType> supportedTypes) {}

    record IncidentResponse(
            String verificationId,
            VerificationType type,
            String provider,
            String errorCode,
            long latencyMs,
            String continuedWith,
            Instant occurredAt) {}
}
