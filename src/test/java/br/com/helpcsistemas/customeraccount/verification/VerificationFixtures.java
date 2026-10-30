package br.com.helpcsistemas.customeraccount.verification;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultRules;
import br.com.helpcsistemas.customeraccount.verification.domain.model.InconclusiveAction;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

public final class VerificationFixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
    public static final TenantId TENANT = TenantId.of("tenant-a");
    public static final ProviderId PRIMARY = ProviderId.of("primary");
    public static final ProviderId FALLBACK = ProviderId.of("fallback");

    private VerificationFixtures() {}

    public static VerificationRule rule(ProviderId primary, ProviderId fallback) {
        return new VerificationRule(
                VerificationType.FACE_MATCH, primary, fallback, 70, InconclusiveAction.MANUAL_REVIEW, true);
    }

    public static ProviderResult result(ProviderDecision decision, Integer score) {
        return new ProviderResult(decision, score, List.of(), "tx-1", 10, "{\"raw\":true}");
    }

    /** Validação de posse (OTP): sem score mínimo. */
    public static VerificationRule otpRule(ProviderId primary, ProviderId fallback) {
        return new VerificationRule(
                VerificationType.EMAIL_OWNERSHIP, primary, fallback, null, InconclusiveAction.REJECT, true);
    }

    public static VerificationRule identityRule(ProviderId primary, ProviderId fallback) {
        return new VerificationRule(
                VerificationType.IDENTITY,
                primary,
                fallback,
                70,
                InconclusiveAction.MANUAL_REVIEW,
                true,
                new HostedResultRules(
                        70,
                        HostedResultRules.Action.MANUAL_REVIEW,
                        List.of("DOCUMENT_TAMPERED"),
                        List.of("NEEDS_REVIEW"),
                        HostedResultRules.Action.ERROR,
                        90,
                        List.of("https://front.example/app")));
    }
}
