package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;

/** Novo e-mail entra como não verificado. */
public record EmailChanged(CustomerId customerId, TenantId changedBy, Email email, Instant occurredAt)
        implements CustomerEvent {}
