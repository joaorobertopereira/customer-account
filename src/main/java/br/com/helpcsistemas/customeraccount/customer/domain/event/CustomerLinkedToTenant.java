package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;

/** O tenant passa a ter acesso aos dados do cliente. */
public record CustomerLinkedToTenant(CustomerId customerId, TenantId tenantId, Instant occurredAt)
        implements CustomerEvent {}
