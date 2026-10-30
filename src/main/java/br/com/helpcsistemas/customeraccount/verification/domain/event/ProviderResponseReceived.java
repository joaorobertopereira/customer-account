package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.time.Instant;

public record ProviderResponseReceived(
        VerificationId verificationId, ProviderId provider, ProviderResult result, Instant occurredAt)
        implements VerificationEvent {}
