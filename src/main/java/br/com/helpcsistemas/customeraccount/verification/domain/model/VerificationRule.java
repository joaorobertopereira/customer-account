package br.com.helpcsistemas.customeraccount.verification.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Objects;

/**
 * Regra do tenant para um tipo de validação.
 *
 * @param fallback provedor de contingência, usado automaticamente se o primário falhar tecnicamente (opcional)
 * @param minimumScore score mínimo (0–100) para aprovar; nulo = aceita o veredito do provedor
 */
public record VerificationRule(
        VerificationType type,
        ProviderId primary,
        ProviderId fallback,
        Integer minimumScore,
        InconclusiveAction onInconclusive,
        boolean enabled,
        HostedResultRules hostedResultRules) {

    public VerificationRule(
            VerificationType type,
            ProviderId primary,
            ProviderId fallback,
            Integer minimumScore,
            InconclusiveAction onInconclusive,
            boolean enabled) {
        this(type, primary, fallback, minimumScore, onInconclusive, enabled, null);
    }

    public VerificationRule {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(onInconclusive, "onInconclusive");
        if (primary.equals(fallback)) {
            throw new InvalidValueException(
                    "INVALID_FALLBACK", "O provedor de contingência deve ser diferente do primário");
        }
        if (minimumScore != null && (minimumScore < 0 || minimumScore > 100)) {
            throw new InvalidValueException("INVALID_MINIMUM_SCORE", "Score mínimo deve estar entre 0 e 100");
        }
        if (type == VerificationType.IDENTITY && hostedResultRules == null) {
            hostedResultRules = HostedResultRules.defaults(minimumScore);
        }
    }

    public boolean hasFallback() {
        return fallback != null;
    }
}
