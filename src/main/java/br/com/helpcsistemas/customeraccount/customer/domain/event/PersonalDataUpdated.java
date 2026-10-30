package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;

public record PersonalDataUpdated(
        CustomerId customerId, TenantId updatedBy, PersonalData personalData, Instant occurredAt)
        implements CustomerEvent {}
