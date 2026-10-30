package br.com.helpcsistemas.customeraccount.verification.adapter.in.scheduler;

import br.com.helpcsistemas.customeraccount.verification.application.port.in.VerificationLifecycleUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expira validações cujo desafio (OTP) não foi respondido no prazo. Seguro com várias instâncias (concorrência otimista). */
@Component
class VerificationExpirationScheduler {

    private final VerificationLifecycleUseCase lifecycle;

    VerificationExpirationScheduler(VerificationLifecycleUseCase lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${app.verification.expiration-check-interval:30s}")
    public void expire() {
        lifecycle.expireDueChallenges();
    }
}
