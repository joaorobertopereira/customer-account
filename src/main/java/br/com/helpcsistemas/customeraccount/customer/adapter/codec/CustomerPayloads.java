package br.com.helpcsistemas.customeraccount.customer.adapter.codec;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.BrazilianState;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CountryCode;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MaritalStatus;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonName;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Esquemas versionados (v1) dos payloads persistidos. Ficam desacoplados das classes de domínio: renomear um campo
 * no domínio não quebra eventos antigos. Mudanças incompatíveis = novo tipo (v2) + upcaster no codec.
 */
public final class CustomerPayloads {

    private CustomerPayloads() {}

    public record PersonalDataV1(
            String fullName,
            String socialName,
            LocalDate birthDate,
            String motherName,
            MaritalStatus maritalStatus,
            String nationality) {

        public static PersonalDataV1 from(PersonalData data) {
            return new PersonalDataV1(
                    data.fullName().value(),
                    data.socialName() == null ? null : data.socialName().value(),
                    data.birthDate(),
                    data.motherName() == null ? null : data.motherName().value(),
                    data.maritalStatus(),
                    data.nationality().value());
        }

        public PersonalData toDomain() {
            return new PersonalData(
                    PersonName.of(fullName),
                    PersonName.ofNullable(socialName),
                    birthDate,
                    PersonName.ofNullable(motherName),
                    maritalStatus,
                    CountryCode.ofNullable(nationality));
        }
    }

    public record AddressV1(
            String zipCode,
            String street,
            String number,
            String complement,
            String district,
            String city,
            String state) {

        public static AddressV1 from(Address address) {
            return address == null
                    ? null
                    : new AddressV1(
                            address.zipCode(),
                            address.street(),
                            address.number(),
                            address.complement(),
                            address.district(),
                            address.city(),
                            address.state().name());
        }

        public Address toDomain() {
            return new Address(zipCode, street, number, complement, district, city, BrazilianState.of(state));
        }

        static Address toDomainOrNull(AddressV1 address) {
            return address == null ? null : address.toDomain();
        }
    }

    record RegisteredV1(
            String customerId,
            String registeredBy,
            String cpf,
            PersonalDataV1 personalData,
            String email,
            String mobilePhone,
            AddressV1 address,
            Instant occurredAt) {}

    record LinkedToTenantV1(String customerId, String tenantId, Instant occurredAt) {}

    record PersonalDataUpdatedV1(
            String customerId, String updatedBy, PersonalDataV1 personalData, Instant occurredAt) {}

    record AddressChangedV1(String customerId, String changedBy, AddressV1 address, Instant occurredAt) {}

    record EmailChangedV1(String customerId, String changedBy, String email, Instant occurredAt) {}

    record MobilePhoneChangedV1(String customerId, String changedBy, String mobilePhone, Instant occurredAt) {}

    record ErasedV1(String customerId, String requestedBy, Instant occurredAt) {}
}
