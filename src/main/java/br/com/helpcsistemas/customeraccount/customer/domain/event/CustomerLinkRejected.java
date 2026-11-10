package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.UUID;

public record CustomerLinkRejected(
        CustomerId customerId, TenantId tenantId, UUID requestId, String reason, Instant occurredAt)
        implements CustomerEvent {}
