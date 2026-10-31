package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.ChallengeResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.ConfigureRuleRequest;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.DeliveryResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.FailoverResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.HostedLinkResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.IncidentResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.PolicyChangeResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.PolicyResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.ProviderFailureResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.ProviderResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.RuleResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.VerificationResponse;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderDescriptor;
import br.com.helpcsistemas.customeraccount.verification.application.query.ProviderIncidentView;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationPolicyView;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultRules;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;

final class VerificationWebMapper {

    private VerificationWebMapper() {}

    static VerificationRule toRule(VerificationType type, ConfigureRuleRequest request) {
        return new VerificationRule(
                type,
                ProviderId.of(request.primaryProvider()),
                ProviderId.ofNullable(request.fallbackProvider()),
                request.minimumScore(),
                request.onInconclusive(),
                request.enabled(),
                type == VerificationType.IDENTITY
                        ? new HostedResultRules(
                                request.minimumScore(),
                                request.scoreBelowAction() == null
                                        ? HostedResultRules.Action.REJECT
                                        : request.scoreBelowAction(),
                                request.fraudReasonCodes(),
                                request.manualReviewReasonCodes(),
                                request.onProviderError() == null
                                        ? HostedResultRules.Action.ERROR
                                        : request.onProviderError(),
                                request.validityDays() == null ? 365 : request.validityDays(),
                                request.allowedReturnUrlPrefixes())
                        : null);
    }

    static VerificationResponse toResponse(VerificationView v) {
        return new VerificationResponse(
                v.id().toString(),
                v.customerId(),
                v.type(),
                v.status(),
                v.outcome(),
                v.provider().value(),
                v.score(),
                v.reasonCodes(),
                v.providerTransactionId(),
                v.policyVersion(),
                new FailoverResponse(
                        v.failedOver(),
                        v.failures().stream()
                                .map(f -> new ProviderFailureResponse(
                                        f.provider().value(), f.errorCode(), f.latencyMs(), f.occurredAt()))
                                .toList()),
                v.challengeExpiresAt() == null
                        ? null
                        : new ChallengeResponse(
                                v.challengeExpiresAt(),
                                v.challengeAttempts(),
                                Math.max(0, VerificationProcess.MAX_CHALLENGE_ATTEMPTS - v.challengeAttempts())),
                v.deliveredAt() == null && !v.deliveryFailed()
                        ? null
                        : new DeliveryResponse(v.deliveredAt(), v.deliveryFailed()),
                v.hostedLink() == null ? null : new HostedLinkResponse(v.hostedLink(), v.hostedLinkExpiresAt()),
                v.validUntil(),
                v.reused(),
                v.requestedAt(),
                v.concludedAt());
    }

    static PolicyResponse toResponse(VerificationPolicyView policy) {
        return new PolicyResponse(
                policy.tenantId().value(),
                policy.version(),
                policy.rules().stream().map(VerificationWebMapper::toResponse).toList());
    }

    static PolicyChangeResponse toResponse(VerificationRuleConfigured change) {
        return new PolicyChangeResponse(
                change.rule().type(),
                toResponse(change.previous()),
                toResponse(change.rule()),
                change.changedBy(),
                change.reason(),
                change.occurredAt());
    }

    static ProviderResponse toResponse(ProviderDescriptor provider) {
        return new ProviderResponse(provider.id().value(), provider.supportedTypes());
    }

    static IncidentResponse toResponse(ProviderIncidentView incident) {
        return new IncidentResponse(
                incident.verificationId().toString(),
                incident.type(),
                incident.provider().value(),
                incident.errorCode(),
                incident.latencyMs(),
                incident.continuedWith() == null
                        ? null
                        : incident.continuedWith().value(),
                incident.occurredAt());
    }

    private static RuleResponse toResponse(VerificationRule rule) {
        return rule == null
                ? null
                : new RuleResponse(
                        rule.type(),
                        rule.primary().value(),
                        rule.hasFallback() ? rule.fallback().value() : null,
                        rule.minimumScore(),
                        rule.onInconclusive(),
                        rule.enabled(),
                        rule.hostedResultRules());
    }
}
