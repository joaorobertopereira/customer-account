package br.com.helpcsistemas.customeraccount.verification.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Aplica a política congelada no pedido à resposta assíncrona do provedor. */
public final class HostedResultClassifier {

    private HostedResultClassifier() {}

    public static Classification classify(HostedResult result, HostedResultRules rules) {
        var signals = new HashSet<String>(result.reasonCodes());
        signals.addAll(result.fraudSignals());
        if (result.status() == HostedResultStatus.FRAUD_DETECTED || intersects(signals, rules.fraudReasonCodes())) {
            return new Classification(VerificationOutcome.FRAUD_SUSPECTED, null);
        }
        if (result.status() == HostedResultStatus.PROVIDER_ERROR) {
            return new Classification(
                    rules.onProviderError() == HostedResultRules.Action.MANUAL_REVIEW
                            ? VerificationOutcome.MANUAL_REVIEW
                            : VerificationOutcome.ERROR,
                    null);
        }
        if (result.status() == HostedResultStatus.ABANDONED) {
            return new Classification(VerificationOutcome.EXPIRED, null);
        }
        if (result.status() == HostedResultStatus.USER_FAILED) {
            return new Classification(
                    intersects(signals, rules.manualReviewReasonCodes())
                            ? VerificationOutcome.MANUAL_REVIEW
                            : VerificationOutcome.REJECTED,
                    null);
        }
        if (rules.minimumScore() != null && (result.score() == null || result.score() < rules.minimumScore())) {
            return new Classification(
                    rules.scoreBelowAction() == HostedResultRules.Action.MANUAL_REVIEW
                            ? VerificationOutcome.MANUAL_REVIEW
                            : VerificationOutcome.REJECTED,
                    null);
        }
        if (intersects(signals, rules.manualReviewReasonCodes())) {
            return new Classification(VerificationOutcome.MANUAL_REVIEW, null);
        }
        return new Classification(
                VerificationOutcome.APPROVED, result.occurredAt().plus(rules.validityDays(), ChronoUnit.DAYS));
    }

    private static boolean intersects(Set<String> values, List<String> configured) {
        return values.stream().anyMatch(configured::contains);
    }

    public record Classification(VerificationOutcome outcome, Instant validUntil) {}
}
