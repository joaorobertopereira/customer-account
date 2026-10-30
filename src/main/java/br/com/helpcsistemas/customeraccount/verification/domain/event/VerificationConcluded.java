package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationOutcome;
import java.time.Instant;
import java.util.List;

/** Veredito final, após aplicar a regra do tenant (score mínimo, ação para inconclusivo) ao retorno do provedor. */
public record VerificationConcluded(
        VerificationId verificationId,
        VerificationOutcome outcome,
        ProviderId provider,
        Integer score,
        List<String> reasonCodes,
        String providerTransactionId,
        Instant occurredAt)
        implements VerificationEvent {

    public VerificationConcluded {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
