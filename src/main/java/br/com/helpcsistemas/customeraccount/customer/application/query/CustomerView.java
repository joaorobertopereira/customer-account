package br.com.helpcsistemas.customeraccount.customer.application.query;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerStatus;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Visão de leitura do cliente (desnormalizada). {@code version} = revisão do último evento aplicado, usada para
 * idempotência da projeção e para o cliente saber quão atualizada está a leitura.
 */
public record CustomerView(
        CustomerId id,
        long version,
        CustomerStatus status,
        Cpf cpf,
        PersonalData personalData,
        Email email,
        boolean emailVerified,
        MobilePhone mobilePhone,
        boolean mobilePhoneVerified,
        Address address,
        Set<TenantId> tenants,
        Instant createdAt,
        Instant updatedAt) {

    public CustomerView {
        tenants = Set.copyOf(tenants);
    }

    public boolean visibleTo(TenantId tenant) {
        return status == CustomerStatus.ACTIVE && tenants.contains(tenant);
    }

    public CustomerView withTenant(TenantId tenant, long revision, Instant at) {
        var linked = new HashSet<>(tenants);
        linked.add(tenant);
        return new CustomerView(
                id,
                revision,
                status,
                cpf,
                personalData,
                email,
                emailVerified,
                mobilePhone,
                mobilePhoneVerified,
                address,
                linked,
                createdAt,
                at);
    }

    public CustomerView withPersonalData(PersonalData data, long revision, Instant at) {
        return new CustomerView(
                id,
                revision,
                status,
                cpf,
                data,
                email,
                emailVerified,
                mobilePhone,
                mobilePhoneVerified,
                address,
                tenants,
                createdAt,
                at);
    }

    public CustomerView withAddress(Address newAddress, long revision, Instant at) {
        return new CustomerView(
                id,
                revision,
                status,
                cpf,
                personalData,
                email,
                emailVerified,
                mobilePhone,
                mobilePhoneVerified,
                newAddress,
                tenants,
                createdAt,
                at);
    }

    public CustomerView withEmail(Email newEmail, long revision, Instant at) {
        return new CustomerView(
                id,
                revision,
                status,
                cpf,
                personalData,
                newEmail,
                false,
                mobilePhone,
                mobilePhoneVerified,
                address,
                tenants,
                createdAt,
                at);
    }

    public CustomerView withMobilePhone(MobilePhone newPhone, long revision, Instant at) {
        return new CustomerView(
                id,
                revision,
                status,
                cpf,
                personalData,
                email,
                emailVerified,
                newPhone,
                false,
                address,
                tenants,
                createdAt,
                at);
    }
}
