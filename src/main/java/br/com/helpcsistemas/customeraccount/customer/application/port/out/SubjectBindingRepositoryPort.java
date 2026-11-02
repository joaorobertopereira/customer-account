package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Vínculo imutável entre o subject do provedor OIDC e um único cadastro global. */
public interface SubjectBindingRepositoryPort {

    boolean bindIfUnboundOrSame(String subject, UUID customerId);

    Optional<UUID> findCustomerId(String subject);
}
