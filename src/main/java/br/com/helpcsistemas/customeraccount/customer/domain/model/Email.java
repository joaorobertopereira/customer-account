package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** E-mail normalizado (trim + minúsculas). Validação sintática; posse é verificada no módulo de verificação. */
public record Email(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern FORMAT = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

    public Email {
        Objects.requireNonNull(value, "email");
        // Limite de tamanho antes da regex evita abuso de backtracking.
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_EMAIL", "E-mail inválido");
        }
    }

    public static Email of(String raw) {
        if (raw == null) {
            throw new InvalidValueException("INVALID_EMAIL", "E-mail inválido");
        }
        return new Email(raw.trim().toLowerCase(Locale.ROOT));
    }

    public String masked() {
        int at = value.indexOf('@');
        return value.charAt(0) + "***" + value.substring(at);
    }

    @Override
    public String toString() {
        return masked();
    }
}
