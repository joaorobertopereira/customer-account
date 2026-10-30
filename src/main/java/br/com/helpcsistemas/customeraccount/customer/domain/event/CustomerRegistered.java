package br.com.helpcsistemas.customeraccount.customer.domain.event;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;

/** @param address opcional no cadastro inicial */
public record CustomerRegistered(
        CustomerId customerId,
        TenantId registeredBy,
        Cpf cpf,
        PersonalData personalData,
        Email email,
        MobilePhone mobilePhone,
        Address address,
        Instant occurredAt)
        implements CustomerEvent {}
