package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;
import java.util.List;

/** Link é credencial de acesso à sessão do titular e deve ser persistido cifrado. */
public record VerificationLinkIssued(
        VerificationId verificationId,
        ProviderId provider,
        String sessionId,
        String url,
        Instant expiresAt,
        List<String> sharedFields,
        Instant occurredAt)
        implements VerificationEvent {

    public VerificationLinkIssued {
        sharedFields = List.copyOf(sharedFields);
    }
}
