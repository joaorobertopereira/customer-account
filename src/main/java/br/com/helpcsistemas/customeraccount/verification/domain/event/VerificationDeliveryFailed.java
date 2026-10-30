package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

/** Todas as tentativas de entrega ao webhook do tenant falharam; o resultado segue disponível para consulta. */
public record VerificationDeliveryFailed(
        VerificationId verificationId, int attempts, Integer lastHttpStatus, Instant occurredAt)
        implements VerificationEvent {}
