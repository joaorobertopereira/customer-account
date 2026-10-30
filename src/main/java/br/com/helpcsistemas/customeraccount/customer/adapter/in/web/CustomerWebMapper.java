package br.com.helpcsistemas.customeraccount.customer.adapter.in.web;

import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.AddressRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.PersonalDataRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.RegisterCustomerRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerResponses.AddressResponse;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerResponses.ContactResponse;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerResponses.CustomerResponse;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerResponses.PersonalDataResponse;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.RegisterCustomerCommand;
import br.com.helpcsistemas.customeraccount.customer.application.query.CustomerView;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.BrazilianState;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CountryCode;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonName;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;

/** Conversão HTTP ⇄ domínio. Value objects validam e normalizam (CPF, e-mail, celular, nomes). */
final class CustomerWebMapper {

    private CustomerWebMapper() {}

    static RegisterCustomerCommand toCommand(TenantId tenant, RegisterCustomerRequest request) {
        return new RegisterCustomerCommand(
                tenant,
                Cpf.of(request.cpf()),
                toPersonalData(request.personalData()),
                Email.of(request.email()),
                MobilePhone.of(request.mobilePhone()),
                request.address() == null ? null : toAddress(request.address()));
    }

    static PersonalData toPersonalData(PersonalDataRequest request) {
        return new PersonalData(
                PersonName.of(request.fullName()),
                PersonName.ofNullable(request.socialName()),
                request.birthDate(),
                PersonName.ofNullable(request.motherName()),
                request.maritalStatus(),
                CountryCode.ofNullable(request.nationality()));
    }

    static Address toAddress(AddressRequest request) {
        return new Address(
                request.zipCode(),
                request.street(),
                request.number(),
                request.complement(),
                request.district(),
                request.city(),
                BrazilianState.of(request.state()));
    }

    static CustomerResponse toResponse(CustomerView view) {
        var data = view.personalData();
        return new CustomerResponse(
                view.id().toString(),
                view.version(),
                view.status().name(),
                view.cpf().value(),
                new PersonalDataResponse(
                        data.fullName().value(),
                        data.socialName() == null ? null : data.socialName().value(),
                        data.birthDate(),
                        data.motherName() == null ? null : data.motherName().value(),
                        data.maritalStatus(),
                        data.nationality().value()),
                new ContactResponse(view.email().value(), view.emailVerified()),
                new ContactResponse(view.mobilePhone().value(), view.mobilePhoneVerified()),
                toResponse(view.address()),
                view.createdAt(),
                view.updatedAt());
    }

    private static AddressResponse toResponse(Address address) {
        return address == null
                ? null
                : new AddressResponse(
                        address.zipCode(),
                        address.street(),
                        address.number(),
                        address.complement(),
                        address.district(),
                        address.city(),
                        address.state().name());
    }
}
