package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

/** Callback assinado, normalizado e correlacionado a uma validação hospedada. */
public record ProviderResultReceived(
        VerificationId verificationId, ProviderId provider, HostedResult result, Instant occurredAt)
        implements VerificationEvent {}
