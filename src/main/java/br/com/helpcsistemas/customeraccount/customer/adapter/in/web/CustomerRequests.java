package br.com.helpcsistemas.customeraccount.customer.adapter.in.web;

import br.com.helpcsistemas.customeraccount.customer.domain.model.MaritalStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** DTOs de entrada. Limites de tamanho protegem contra payloads abusivos; regras finas ficam nos value objects. */
final class CustomerRequests {

    private CustomerRequests() {}

    record RegisterCustomerRequest(
            @NotBlank @Size(max = 14) String cpf,
            @NotNull @Valid PersonalDataRequest personalData,
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 20) String mobilePhone,
            @Valid AddressRequest address) {}

    record PersonalDataRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 150) String socialName,
            @NotNull @Past LocalDate birthDate,
            @Size(max = 150) String motherName,
            MaritalStatus maritalStatus,
            @Size(min = 2, max = 2) String nationality) {}

    record AddressRequest(
            @NotBlank @Size(max = 9) String zipCode,
            @NotBlank @Size(max = 120) String street,
            @NotBlank @Size(max = 10) String number,
            @Size(max = 60) String complement,
            @NotBlank @Size(max = 60) String district,
            @NotBlank @Size(max = 60) String city,
            @NotBlank @Size(min = 2, max = 2) String state) {}

    record ChangeEmailRequest(@NotBlank @Size(max = 254) String email) {}

    record ChangeMobilePhoneRequest(
            @NotBlank @Size(max = 20) String mobilePhone) {}

    /** CPF no corpo (não na URL): evita que o dado fique em logs de proxy/gateway e no histórico do navegador. */
    record SearchByCpfRequest(@NotBlank @Size(max = 14) String cpf) {}
}
