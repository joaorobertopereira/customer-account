package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

/** Resposta incorreta ao desafio (o código informado nunca é registrado). */
public record ChallengeAnswerRejected(VerificationId verificationId, int attempt, Instant occurredAt)
        implements VerificationEvent {}
