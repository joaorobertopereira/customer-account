package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** País no padrão ISO 3166-1 alfa-2 (ex.: BR). */
public record CountryCode(String value) {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public static final CountryCode BRAZIL = new CountryCode("BR");

    public CountryCode {
        Objects.requireNonNull(value, "country");
        if (!ISO_COUNTRIES.contains(value)) {
            throw new InvalidValueException("INVALID_COUNTRY", "País inválido");
        }
    }

    public static CountryCode ofNullable(String raw) {
        return raw == null || raw.isBlank()
                ? BRAZIL
                : new CountryCode(raw.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
