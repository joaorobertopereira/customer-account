package br.com.helpcsistemas.customeraccount.customer;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.UUID;

public record CustomerTenantUnlinked(UUID customerId, TenantId tenantId, Instant occurredAt) {}
