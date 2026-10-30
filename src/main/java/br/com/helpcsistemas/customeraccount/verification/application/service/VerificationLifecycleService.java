package br.com.helpcsistemas.customeraccount.verification.application.service;

import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.VerificationLifecycleUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProcessRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationResultNotifierPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import java.time.Clock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class VerificationLifecycleService implements VerificationLifecycleUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerificationLifecycleService.class);
    static final int EXPIRATION_BATCH = 100;

    private final VerificationProcessRepositoryPort processes;
    private final VerificationViewRepositoryPort views;
    private final VerificationResultNotifierPort notifier;
    private final Clock clock;

    VerificationLifecycleService(
            VerificationProcessRepositoryPort processes,
            VerificationViewRepositoryPort views,
            VerificationResultNotifierPort notifier,
            Clock clock) {
        this.processes = processes;
        this.views = views;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    public int expireDueChallenges() {
        int expired = 0;
        for (var id : views.findAwaitingExpiredBefore(clock.instant(), EXPIRATION_BATCH)) {
            var process = processes.findById(id);
            if (process.isPresent() && process.get().expireIfDue(clock)) {
                // Outra instância pode ter expirado/confirmado antes: o conflito é esperado e ignorado.
                if (saveIgnoringConflict(process.get())) {
                    expired++;
                    log.atInfo().addKeyValue("verification.id", id).log("Validação expirada sem resposta do titular");
                }
            }
        }
        return expired;
    }

    @Override
    public void notifyResult(VerificationId id) {
        processes
                .findById(id)
                .filter(p -> p.status() == VerificationStatus.COMPLETED)
                .map(VerificationView::of)
                .ifPresent(notifier::notifyResult);
    }

    @Override
    public void recordDelivered(VerificationId id, int httpStatus, int attempts) {
        update(id, p -> p.markDelivered(httpStatus, attempts, clock));
    }

    @Override
    public void recordDeliveryFailed(VerificationId id, int attempts, Integer lastHttpStatus) {
        update(id, p -> p.markDeliveryFailed(attempts, lastHttpStatus, clock));
        log.atWarn()
                .addKeyValue("verification.id", id)
                .addKeyValue("delivery.attempts", attempts)
                .log("Resultado não entregue ao webhook do tenant após todas as tentativas");
    }

    private void update(VerificationId id, Consumer<VerificationProcess> change) {
        for (int attempt = 0; attempt < 3; attempt++) {
            var process = processes.findById(id);
            if (process.isEmpty()) {
                return;
            }
            change.accept(process.get());
            if (saveIgnoringConflict(process.get())) {
                return;
            }
        }
    }

    private boolean saveIgnoringConflict(VerificationProcess process) {
        try {
            processes.save(process);
            return true;
        } catch (ConcurrencyConflictException ex) {
            return false;
        }
    }
}
