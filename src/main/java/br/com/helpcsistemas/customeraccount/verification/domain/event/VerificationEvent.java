package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.shared.domain.DomainEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;

public sealed interface VerificationEvent extends DomainEvent
        permits VerificationRequested,
                VerificationSubmittedToProvider,
                ProviderCallFailed,
                ProviderFailedOver,
                ProviderResponseReceived,
                ChallengeIssued,
                ChallengeAnswerRejected,
                VerificationLinkIssued,
                ProviderResultReceived,
                LateProviderResultIgnored,
                VerificationConcluded,
                VerificationValidityExpired,
                VerificationResultDelivered,
                VerificationDeliveryFailed {

    VerificationId verificationId();
}
