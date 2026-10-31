package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;

public record CustomerUnlinkedFromTenant(CustomerId customerId, TenantId tenantId, Instant occurredAt)
        implements CustomerEvent {}
