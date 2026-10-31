package br.com.helpcsistemas.customeraccount.verification.adapter.in.scheduler;

import br.com.helpcsistemas.customeraccount.verification.application.port.in.VerificationLifecycleUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expira desafios, links hospedados e aprovações vencidas; concorrência otimista permite várias instâncias. */
@Component
class VerificationExpirationScheduler {

    private final VerificationLifecycleUseCase lifecycle;

    VerificationExpirationScheduler(VerificationLifecycleUseCase lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${app.verification.expiration-check-interval:30s}")
    public void expire() {
        lifecycle.expireDueChallenges();
        lifecycle.expireDueValidity();
    }
}
