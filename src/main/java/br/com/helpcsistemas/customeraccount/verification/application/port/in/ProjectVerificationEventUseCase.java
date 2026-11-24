package br.com.helpcsistemas.customeraccount.verification.application.port.in;

import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationEvent;
import java.util.UUID;

public interface ProjectVerificationEventUseCase {

    /** Atualiza os read models (resultado e incidentes). Idempotente. */
    void project(VerificationEvent event, long revision);

    /** Remove dados derivados de um titular eliminado (LGPD). */
    void eraseCustomerData(UUID customerId);

    /** Descarta o read model de validações para que seja reconstruído a partir do histórico. */
    void reset();
}
