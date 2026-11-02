package br.com.helpcsistemas.customeraccount.customer;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** API pública do módulo: dados do titular para outros módulos (ex.: validação), sempre com isolamento de tenant. */
public interface CustomerDirectory {

    /** Lê do event store (consistência forte); vazio se não existir, eliminado ou não vinculado ao tenant. */
    Optional<CustomerSnapshot> findLinkedCustomer(TenantId tenant, UUID customerId);

    Optional<java.time.Instant> linkedAt(TenantId tenant, UUID customerId);

    /**
     * Localiza cadastro já existente pelo e-mail verificado no JWT e revalida o candidato no event store para o tenant.
     * Não cria nem vincula cadastros.
     */
    Optional<CustomerSnapshot> findLinkedCustomerByEmail(TenantId tenant, String verifiedEmail);

    /** Retorna os vínculos atuais do titular somente após confirmar o vínculo solicitado no event store. */
    Set<TenantId> findLinkedTenantMemberships(TenantId tenant, UUID customerId);
}
