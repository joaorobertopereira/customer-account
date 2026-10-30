package br.com.helpcsistemas.customeraccount.customer;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.BrazilianState;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CountryCode;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MaritalStatus;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonName;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Dados de teste fictícios (CPFs gerados apenas para validar dígitos verificadores). */
public final class CustomerFixtures {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
    public static final TenantId TENANT_A = TenantId.of("tenant-a");
    public static final TenantId TENANT_B = TenantId.of("tenant-b");
    public static final String CPF = "52998224725";
    public static final String OTHER_CPF = "11144477735";

    private CustomerFixtures() {}

    public static Cpf cpf() {
        return Cpf.of(CPF);
    }

    public static PersonalData personalData() {
        return new PersonalData(
                PersonName.of("Maria da Silva Souza"),
                null,
                LocalDate.of(1990, 5, 20),
                PersonName.of("Ana da Silva"),
                MaritalStatus.MARRIED,
                CountryCode.BRAZIL);
    }

    public static Email email() {
        return Email.of("maria.souza@example.com");
    }

    public static MobilePhone mobilePhone() {
        return MobilePhone.of("(11) 91234-5678");
    }

    public static Address address() {
        return new Address(
                "01310-100", "Avenida Paulista", "1000", "Apto 101", "Bela Vista", "São Paulo", BrazilianState.SP);
    }

    public static Customer newCustomer(CustomerId id) {
        return Customer.register(id, TENANT_A, cpf(), personalData(), email(), mobilePhone(), address(), CLOCK);
    }

    /** Cliente como se tivesse sido carregado do event store (registrado + vinculado = revisão 1). */
    public static Customer persistedCustomer(CustomerId id) {
        var customer = newCustomer(id);
        customer.markCommitted(1);
        return customer;
    }
}
