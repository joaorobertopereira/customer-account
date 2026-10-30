package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import java.util.Optional;

public interface VerificationProcessRepositoryPort {

    /** Vazio se não existir ou se o titular foi eliminado (chave destruída). */
    Optional<VerificationProcess> findById(VerificationId id);

    void save(VerificationProcess process);
}
