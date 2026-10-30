package br.com.helpcsistemas.customeraccount.customer.adapter.in.web;

import br.com.helpcsistemas.customeraccount.customer.domain.model.MaritalStatus;
import java.time.Instant;
import java.time.LocalDate;

final class CustomerResponses {

    private CustomerResponses() {}

    record RegisteredResponse(String id) {}

    /** @param version revisão do read model; permite ao tenant detectar leitura ainda não atualizada */
    record CustomerResponse(
            String id,
            long version,
            String status,
            String cpf,
            PersonalDataResponse personalData,
            ContactResponse email,
            ContactResponse mobilePhone,
            AddressResponse address,
            Instant createdAt,
            Instant updatedAt) {}

    record PersonalDataResponse(
            String fullName,
            String socialName,
            LocalDate birthDate,
            String motherName,
            MaritalStatus maritalStatus,
            String nationality) {}

    record ContactResponse(String value, boolean verified) {}

    record AddressResponse(
            String zipCode,
            String street,
            String number,
            String complement,
            String district,
            String city,
            String state) {}
}
