package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.DomainEvent;

/** Eventos do agregado Customer. Selado: o compilador garante tratamento exaustivo em projeções e codecs. */
public sealed interface CustomerEvent extends DomainEvent
        permits CustomerRegistered,
                CustomerLinkedToTenant,
                PersonalDataUpdated,
                AddressChanged,
                EmailChanged,
                MobilePhoneChanged,
                CustomerErased {

    CustomerId customerId();
}
