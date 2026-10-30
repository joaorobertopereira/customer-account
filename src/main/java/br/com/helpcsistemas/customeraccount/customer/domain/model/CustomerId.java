package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.Identifiers;
import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Objects;
import java.util.UUID;

/** Identificador pseudônimo do titular (não deriva de CPF/e-mail): pode circular em logs e entre tenants. */
public record CustomerId(UUID value) {

    public CustomerId {
        Objects.requireNonNull(value, "customerId");
    }

    public static CustomerId newId() {
        return new CustomerId(Identifiers.newUuidV7());
    }

    public static CustomerId of(String raw) {
        try {
            return new CustomerId(UUID.fromString(raw));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidValueException("INVALID_CUSTOMER_ID", "Identificador de cliente inválido");
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
