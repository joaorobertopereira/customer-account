package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

/** Falha técnica do provedor. Fica registrada para o tenant mesmo quando a contingência conclui a validação. */
public record ProviderCallFailed(
        VerificationId verificationId, ProviderId provider, String errorCode, long latencyMs, Instant occurredAt)
        implements VerificationEvent {}
