package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;

/** Eliminação solicitada pelo titular (via tenant). Não carrega dados pessoais: é gravado sem cifra. */
public record CustomerErased(CustomerId customerId, TenantId requestedBy, Instant occurredAt)
        implements CustomerEvent {}
