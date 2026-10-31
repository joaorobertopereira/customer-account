package br.com.helpcsistemas.customeraccount.verification.application.port.in;

import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;

/** Ciclo de vida após a solicitação: expiração de desafios e entrega do resultado ao tenant. */
public interface VerificationLifecycleUseCase {

    /** @return quantidade de validações expiradas nesta execução */
    int expireDueChallenges();

    int expireDueValidity();

    /** Agenda a entrega do resultado ao webhook do tenant, se a validação estiver concluída. Idempotente. */
    void notifyResult(VerificationId id);

    void notifyValidityExpired(VerificationId id);

    void recordDelivered(VerificationId id, int httpStatus, int attempts);

    void recordDeliveryFailed(VerificationId id, int attempts, Integer lastHttpStatus);
}
