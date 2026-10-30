package br.com.helpcsistemas.customeraccount.customer;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Optional;
import java.util.UUID;

/** API pública do módulo: dados do titular para outros módulos (ex.: validação), sempre com isolamento de tenant. */
public interface CustomerDirectory {

    /** Lê do event store (consistência forte); vazio se não existir, eliminado ou não vinculado ao tenant. */
    Optional<CustomerSnapshot> findLinkedCustomer(TenantId tenant, UUID customerId);
}
