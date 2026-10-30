package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Objects;
import java.util.regex.Pattern;

/** Nome de pessoa: letras (com acentos), espaço, apóstrofo, ponto e hífen; espaços normalizados. */
public record PersonName(String value) {

    private static final Pattern FORMAT = Pattern.compile("^\\p{L}[\\p{L}' .-]{1,149}$");

    public PersonName {
        Objects.requireNonNull(value, "name");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_NAME", "Nome inválido");
        }
    }

    public static PersonName of(String raw) {
        if (raw == null) {
            throw new InvalidValueException("INVALID_NAME", "Nome inválido");
        }
        return new PersonName(raw.strip().replaceAll("\\s+", " "));
    }

    /** Nome opcional: nulo ou em branco vira {@code null}. */
    public static PersonName ofNullable(String raw) {
        return raw == null || raw.isBlank() ? null : of(raw);
    }

    public boolean hasSurname() {
        return value.indexOf(' ') > 0;
    }

    @Override
    public String toString() {
        int space = value.indexOf(' ');
        return (space > 0 ? value.substring(0, space) : value.charAt(0) + "") + " ***";
    }
}
