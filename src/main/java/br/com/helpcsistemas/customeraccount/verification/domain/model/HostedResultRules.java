package br.com.helpcsistemas.customeraccount.verification.domain.model;

import java.util.List;
import java.util.Objects;

/** Política imutável aplicada ao retorno assíncrono de uma sessão hospedada. */
public record HostedResultRules(
        Integer minimumScore,
        Action scoreBelowAction,
        List<String> fraudReasonCodes,
        List<String> manualReviewReasonCodes,
        Action onProviderError,
        int validityDays,
        List<String> allowedReturnUrlPrefixes) {

    public enum Action {
        REJECT,
        MANUAL_REVIEW,
        ERROR
    }

    public HostedResultRules {
        Objects.requireNonNull(scoreBelowAction, "scoreBelowAction");
        Objects.requireNonNull(onProviderError, "onProviderError");
        fraudReasonCodes = fraudReasonCodes == null ? List.of() : List.copyOf(fraudReasonCodes);
        manualReviewReasonCodes = manualReviewReasonCodes == null ? List.of() : List.copyOf(manualReviewReasonCodes);
        allowedReturnUrlPrefixes = allowedReturnUrlPrefixes == null ? List.of() : List.copyOf(allowedReturnUrlPrefixes);
        if (minimumScore != null && (minimumScore < 0 || minimumScore > 100)) {
            throw new IllegalArgumentException("minimumScore deve estar entre 0 e 100");
        }
        if (scoreBelowAction == Action.ERROR || onProviderError == Action.REJECT) {
            throw new IllegalArgumentException("Ação incompatível com a regra");
        }
        if (validityDays < 1 || validityDays > 3650) {
            throw new IllegalArgumentException("validityDays deve estar entre 1 e 3650");
        }
    }

    public HostedResultRules(
            Integer minimumScore,
            Action scoreBelowAction,
            List<String> fraudReasonCodes,
            List<String> manualReviewReasonCodes,
            Action onProviderError,
            int validityDays) {
        this(
                minimumScore,
                scoreBelowAction,
                fraudReasonCodes,
                manualReviewReasonCodes,
                onProviderError,
                validityDays,
                List.of());
    }

    public static HostedResultRules defaults(Integer minimumScore) {
        return new HostedResultRules(minimumScore, Action.REJECT, List.of(), List.of(), Action.ERROR, 365, List.of());
    }
}
