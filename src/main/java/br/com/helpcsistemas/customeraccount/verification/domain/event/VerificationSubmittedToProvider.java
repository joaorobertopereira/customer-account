package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

public record VerificationSubmittedToProvider(
        VerificationId verificationId, ProviderId provider, int attempt, Instant occurredAt)
        implements VerificationEvent {}
