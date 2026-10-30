package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

/** Resultado entregue ao webhook do tenant. */
public record VerificationResultDelivered(
        VerificationId verificationId, int httpStatus, int attempts, Instant occurredAt) implements VerificationEvent {}
