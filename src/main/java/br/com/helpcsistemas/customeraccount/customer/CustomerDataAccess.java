package br.com.helpcsistemas.customeraccount.customer;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Set;
import java.util.UUID;

/** Finalidades autorizadas, antes da interseção com os escopos OAuth2. */
public interface CustomerDataAccess {
    Set<String> allowedFields(TenantId tenant, UUID customerId);
}
