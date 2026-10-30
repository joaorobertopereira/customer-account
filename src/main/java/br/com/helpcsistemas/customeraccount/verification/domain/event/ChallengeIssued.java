package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

/** O provedor enviou um desafio (ex.: código OTP); a validação aguarda a resposta do titular até {@code expiresAt}. */
public record ChallengeIssued(
        VerificationId verificationId, ProviderId provider, String transactionId, Instant expiresAt, Instant occurredAt)
        implements VerificationEvent {}
