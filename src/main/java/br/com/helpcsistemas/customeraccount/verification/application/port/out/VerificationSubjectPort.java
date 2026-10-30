package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Optional;
import java.util.UUID;

public interface VerificationSubjectPort {

    /** Vazio se o cliente não existir, foi eliminado ou não está vinculado ao tenant. */
    Optional<VerificationSubject> find(TenantId tenant, UUID customerId);
}
