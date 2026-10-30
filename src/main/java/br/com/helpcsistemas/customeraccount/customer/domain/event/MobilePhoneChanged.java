package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;

/** Novo celular entra como não verificado. */
public record MobilePhoneChanged(CustomerId customerId, TenantId changedBy, MobilePhone mobilePhone, Instant occurredAt)
        implements CustomerEvent {}
