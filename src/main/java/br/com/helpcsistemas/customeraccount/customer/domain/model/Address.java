package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Objects;

/** Endereço residencial no Brasil. Complemento é opcional. */
public record Address(
        String zipCode,
        String street,
        String number,
        String complement,
        String district,
        String city,
        BrazilianState state) {

    public Address {
        Objects.requireNonNull(state, "state");
        zipCode = zipCode == null ? "" : zipCode.strip().replace("-", "");
        if (!zipCode.matches("\\d{8}")) {
            throw new InvalidValueException("INVALID_ZIP_CODE", "CEP inválido");
        }
        street = require(street, "street", 120);
        number = require(number, "number", 10);
        complement = complement == null || complement.isBlank() ? null : limit(complement.strip(), "complement", 60);
        district = require(district, "district", 60);
        city = require(city, "city", 60);
    }

    private static String require(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException("INVALID_ADDRESS", "Endereço incompleto: " + field);
        }
        return limit(value.strip(), field, maxLength);
    }

    private static String limit(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new InvalidValueException("INVALID_ADDRESS", "Campo de endereço muito longo: " + field);
        }
        return value;
    }

    @Override
    public String toString() {
        return "Address[city=" + city + ", state=" + state + "]";
    }
}
