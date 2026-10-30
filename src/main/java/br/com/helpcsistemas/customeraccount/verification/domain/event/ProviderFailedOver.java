package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

public record ProviderFailedOver(VerificationId verificationId, ProviderId from, ProviderId to, Instant occurredAt)
        implements VerificationEvent {}
