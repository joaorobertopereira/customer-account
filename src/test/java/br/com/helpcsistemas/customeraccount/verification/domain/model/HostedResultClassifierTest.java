package br.com.helpcsistemas.customeraccount.verification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostedResultClassifierTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private static HostedResult result(
            HostedResultStatus status, Integer score, List<String> reasons, List<String> fraudSignals) {
        return new HostedResult("session-1", status, score, reasons, fraudSignals, "event-1", NOW, "{}");
    }

    @Test
    void approvesWithConfiguredValidity() {
        var rules = new HostedResultRules(
                70, HostedResultRules.Action.REJECT, List.of(), List.of(), HostedResultRules.Action.ERROR, 90);

        var classification =
                HostedResultClassifier.classify(result(HostedResultStatus.COMPLETED, 85, List.of(), List.of()), rules);

        assertThat(classification.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThat(classification.validUntil()).isEqualTo(NOW.plusSeconds(90L * 24 * 60 * 60));
    }

    @Test
    void lowScoreCanRequireManualReview() {
        var rules = new HostedResultRules(
                70, HostedResultRules.Action.MANUAL_REVIEW, List.of(), List.of(), HostedResultRules.Action.ERROR, 365);

        var classification =
                HostedResultClassifier.classify(result(HostedResultStatus.COMPLETED, 50, List.of(), List.of()), rules);

        assertThat(classification.outcome()).isEqualTo(VerificationOutcome.MANUAL_REVIEW);
        assertThat(classification.validUntil()).isNull();
    }

    @Test
    void configuredFraudSignalWinsOverCompletedStatus() {
        var rules = new HostedResultRules(
                70,
                HostedResultRules.Action.REJECT,
                List.of("DOCUMENT_TAMPERED"),
                List.of(),
                HostedResultRules.Action.ERROR,
                365);

        var classification = HostedResultClassifier.classify(
                result(HostedResultStatus.COMPLETED, 99, List.of(), List.of("DOCUMENT_TAMPERED")), rules);

        assertThat(classification.outcome()).isEqualTo(VerificationOutcome.FRAUD_SUSPECTED);
        assertThat(classification.validUntil()).isNull();
    }

    @Test
    void manualReviewReasonsOverrideSuccessfulProviderStatus() {
        var rules = new HostedResultRules(
                null,
                HostedResultRules.Action.REJECT,
                List.of(),
                List.of("NEEDS_REVIEW"),
                HostedResultRules.Action.ERROR,
                365);

        var classification = HostedResultClassifier.classify(
                result(HostedResultStatus.COMPLETED, 99, List.of("NEEDS_REVIEW"), List.of()), rules);

        assertThat(classification.outcome()).isEqualTo(VerificationOutcome.MANUAL_REVIEW);
    }

    @Test
    void mapsProviderErrorAndAbandonmentAccordingToPolicy() {
        var reviewOnError = new HostedResultRules(
                null,
                HostedResultRules.Action.REJECT,
                List.of(),
                List.of(),
                HostedResultRules.Action.MANUAL_REVIEW,
                365);

        assertThat(HostedResultClassifier.classify(
                                result(HostedResultStatus.PROVIDER_ERROR, null, List.of(), List.of()), reviewOnError)
                        .outcome())
                .isEqualTo(VerificationOutcome.MANUAL_REVIEW);
        assertThat(HostedResultClassifier.classify(
                                result(HostedResultStatus.ABANDONED, null, List.of(), List.of()), reviewOnError)
                        .outcome())
                .isEqualTo(VerificationOutcome.EXPIRED);
    }

    @Test
    void validatesRuleBoundaries() {
        assertThatThrownBy(() -> HostedResultRules.defaults(101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HostedResultRules(
                        null, HostedResultRules.Action.REJECT, List.of(), List.of(), HostedResultRules.Action.ERROR, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
