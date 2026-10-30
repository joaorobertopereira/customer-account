package br.com.helpcsistemas.customeraccount.verification.domain.model;

import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.FALLBACK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.PRIMARY;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.TENANT;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.otpRule;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.result;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ChallengeAnswerRejected;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ChallengeIssued;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderCallFailed;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderFailedOver;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderResponseReceived;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationConcluded;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRequested;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationResultDelivered;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationSubmittedToProvider;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationProcessTest {

    private VerificationProcess start(VerificationRule rule) {
        return VerificationProcess.request(VerificationId.newId(), TENANT, UUID.randomUUID(), rule, 3, "tester", CLOCK);
    }

    @Test
    void approvesWhenProviderApprovesAboveMinimumScore() {
        var process = start(rule(PRIMARY, FALLBACK));
        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.APPROVED, 90), CLOCK);

        assertThat(process.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThat(process.status()).isEqualTo(VerificationStatus.COMPLETED);
        assertThat(process.policyVersion()).isEqualTo(3);
        assertThat(process.pendingEvents())
                .hasExactlyElementsOfTypes(
                        VerificationRequested.class,
                        VerificationSubmittedToProvider.class,
                        ProviderResponseReceived.class,
                        VerificationConcluded.class);
    }

    @Test
    void rejectsWhenScoreIsBelowTenantMinimum() {
        var process = start(rule(PRIMARY, null));
        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.APPROVED, 60), CLOCK);

        assertThat(process.outcome()).isEqualTo(VerificationOutcome.REJECTED);
        assertThat(process.reasonCodes()).contains(VerificationProcess.SCORE_BELOW_THRESHOLD);
    }

    @Test
    void inconclusiveFollowsTenantAction() {
        var process = start(rule(PRIMARY, null));
        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.INCONCLUSIVE, 50), CLOCK);

        assertThat(process.outcome()).isEqualTo(VerificationOutcome.MANUAL_REVIEW);
    }

    @Test
    void failsOverToFallbackAndKeepsTheFailureRecorded() {
        var process = start(rule(PRIMARY, FALLBACK));
        process.submit(CLOCK);
        process.providerFailed("TIMEOUT", 5000, CLOCK);

        assertThat(process.isPending()).isTrue();
        assertThat(process.currentProvider()).isEqualTo(FALLBACK);

        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.APPROVED, 95), CLOCK);

        assertThat(process.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThat(process.currentProvider()).isEqualTo(FALLBACK);
        assertThat(process.failedOver()).isTrue();
        assertThat(process.failures()).singleElement().satisfies(f -> {
            assertThat(f.provider()).isEqualTo(PRIMARY);
            assertThat(f.errorCode()).isEqualTo("TIMEOUT");
        });
        assertThat(process.pendingEvents()).hasAtLeastOneElementOfType(ProviderFailedOver.class);
    }

    @Test
    void failsOnlyWhenEveryConfiguredProviderFails() {
        var process = start(rule(PRIMARY, FALLBACK));
        process.submit(CLOCK);
        process.providerFailed("TIMEOUT", 10, CLOCK);
        process.submit(CLOCK);
        process.providerFailed("PROVIDER_ERROR", 10, CLOCK);

        assertThat(process.outcome()).isEqualTo(VerificationOutcome.FAILED);
        assertThat(process.reasonCodes()).containsExactly(VerificationProcess.ALL_PROVIDERS_UNAVAILABLE);
        assertThat(process.pendingEvents().stream().filter(ProviderCallFailed.class::isInstance))
                .hasSize(2);
    }

    @Test
    void rehydratesTheSameState() {
        var process = start(rule(PRIMARY, FALLBACK));
        process.submit(CLOCK);
        process.providerFailed("TIMEOUT", 10, CLOCK);
        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.APPROVED, 95), CLOCK);

        var restored = VerificationProcess.rehydrate(process.pendingEvents(), 6);

        assertThat(restored.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThat(restored.failures()).isEqualTo(process.failures());
        assertThat(restored.version()).isEqualTo(6);
    }

    @Test
    void disabledRuleCannotBeUsed() {
        var disabled =
                new VerificationRule(VerificationType.LIVENESS, PRIMARY, null, null, InconclusiveAction.REJECT, false);

        assertThatThrownBy(() -> start(disabled)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void fallbackMustDifferFromPrimary() {
        assertThatThrownBy(() -> rule(PRIMARY, PRIMARY)).isInstanceOf(InvalidValueException.class);
    }

    private VerificationProcess awaitingChallenge() {
        var process = start(otpRule(PRIMARY, null));
        process.submit(CLOCK);
        process.responseReceived(result(ProviderDecision.CHALLENGE_SENT, null), CLOCK);
        return process;
    }

    @Test
    void challengeWaitsForTheHolderAndApprovesWithTheRightAnswer() {
        var process = awaitingChallenge();

        assertThat(process.status()).isEqualTo(VerificationStatus.AWAITING_CONFIRMATION);
        assertThat(process.isPending()).isFalse();
        assertThat(process.challengeExpiresAt())
                .isEqualTo(CLOCK.instant().plus(VerificationProcess.DEFAULT_CHALLENGE_TTL));
        assertThat(process.providerTransactionId()).isEqualTo("tx-1");

        process.answerChallenge(result(ProviderDecision.REJECTED, null), CLOCK);
        assertThat(process.challengeAttempts()).isEqualTo(1);
        assertThat(process.status()).isEqualTo(VerificationStatus.AWAITING_CONFIRMATION);

        process.answerChallenge(result(ProviderDecision.APPROVED, null), CLOCK);
        assertThat(process.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThat(process.pendingEvents())
                .hasAtLeastOneElementOfType(ChallengeIssued.class)
                .hasAtLeastOneElementOfType(ChallengeAnswerRejected.class);
    }

    @Test
    void rejectsAfterTooManyWrongAnswers() {
        var process = awaitingChallenge();
        for (int i = 0; i < VerificationProcess.MAX_CHALLENGE_ATTEMPTS; i++) {
            process.answerChallenge(result(ProviderDecision.REJECTED, null), CLOCK);
        }

        assertThat(process.outcome()).isEqualTo(VerificationOutcome.REJECTED);
        assertThat(process.reasonCodes()).contains(VerificationProcess.TOO_MANY_ATTEMPTS);
        assertThatThrownBy(() -> process.answerChallenge(result(ProviderDecision.APPROVED, null), CLOCK))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void expiresWhenTheHolderDoesNotAnswerInTime() {
        var process = awaitingChallenge();
        var later = Clock.offset(CLOCK, VerificationProcess.DEFAULT_CHALLENGE_TTL.plusSeconds(1));

        assertThat(process.expireIfDue(CLOCK)).isFalse();
        process.answerChallenge(result(ProviderDecision.APPROVED, null), later);

        assertThat(process.outcome()).isEqualTo(VerificationOutcome.EXPIRED);
        assertThat(process.reasonCodes()).containsExactly(VerificationProcess.CHALLENGE_EXPIRED);
        assertThat(process.expireIfDue(later)).isFalse();
    }

    @Test
    void technicalFailureWhileCheckingKeepsTheChallengeOpen() {
        var process = awaitingChallenge();
        process.challengeCheckFailed("TIMEOUT", 5000, CLOCK);

        assertThat(process.status()).isEqualTo(VerificationStatus.AWAITING_CONFIRMATION);
        assertThat(process.failures()).extracting(ProviderFailure::errorCode).containsExactly("TIMEOUT");
    }

    @Test
    void recordsWebhookDeliveryOnlyOnceAfterConclusion() {
        var process = awaitingChallenge();
        process.markDelivered(200, 1, CLOCK);
        assertThat(process.deliveredAt()).isNull();

        process.answerChallenge(result(ProviderDecision.APPROVED, null), CLOCK);
        process.markDelivered(200, 1, CLOCK);
        process.markDelivered(200, 2, CLOCK);
        process.markDeliveryFailed(3, 500, CLOCK);

        assertThat(process.deliveredAt()).isEqualTo(CLOCK.instant());
        assertThat(process.deliveryFailed()).isFalse();
        assertThat(process.pendingEvents().stream().filter(VerificationResultDelivered.class::isInstance))
                .hasSize(1);
    }

    @Test
    void rehydratesChallengeState() {
        var process = awaitingChallenge();
        process.answerChallenge(result(ProviderDecision.REJECTED, null), CLOCK);

        var restored = VerificationProcess.rehydrate(
                process.pendingEvents(), process.pendingEvents().size() - 1);

        assertThat(restored.status()).isEqualTo(VerificationStatus.AWAITING_CONFIRMATION);
        assertThat(restored.challengeAttempts()).isEqualTo(1);
        assertThat(restored.challengeExpiresAt()).isEqualTo(process.challengeExpiresAt());
    }
}
