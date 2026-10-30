package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;

public record AddressChanged(CustomerId customerId, TenantId changedBy, Address address, Instant occurredAt)
        implements CustomerEvent {}
