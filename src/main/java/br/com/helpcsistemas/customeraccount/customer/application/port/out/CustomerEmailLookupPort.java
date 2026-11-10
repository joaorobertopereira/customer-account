package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.List;
import java.util.UUID;

/** Busca candidatos por índice cego; todo resultado deve ser revalidado no repositório de escrita. */
public interface CustomerEmailLookupPort {

    List<UUID> findCandidates(TenantId tenant, Email email);
}
