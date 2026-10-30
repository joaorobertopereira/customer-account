package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

/** A aprovação deixou de ser válida conforme a política congelada no pedido. */
public record VerificationValidityExpired(VerificationId verificationId, Instant validUntil, Instant occurredAt)
        implements VerificationEvent {}
