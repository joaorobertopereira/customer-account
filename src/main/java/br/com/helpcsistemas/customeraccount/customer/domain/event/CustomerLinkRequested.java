package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.UUID;

public record CustomerLinkRequested(
        CustomerId customerId,
        TenantId tenantId,
        UUID requestId,
        PersonalData suggestedPersonalData,
        Email suggestedEmail,
        MobilePhone suggestedMobilePhone,
        Address suggestedAddress,
        Instant expiresAt,
        Instant occurredAt)
        implements CustomerEvent {
    @Override
    public String toString() {
        return "CustomerLinkRequested[" + requestId + "]";
    }
}
