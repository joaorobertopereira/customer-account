package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.UUID;

public record CustomerLinkApproved(CustomerId customerId, TenantId tenantId, UUID requestId, Instant occurredAt)
        implements CustomerEvent {}
