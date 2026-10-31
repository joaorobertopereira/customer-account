package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

/** Callback tardio registrado sem alterar um resultado já concluído. */
public record LateProviderResultIgnored(
        VerificationId verificationId, ProviderId provider, String providerEventId, String reason, Instant occurredAt)
        implements VerificationEvent {}
