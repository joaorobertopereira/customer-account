package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Objects;
import java.util.regex.Pattern;

/** Celular brasileiro em E.164 ({@code +55DD9XXXXXXXX}). */
public record MobilePhone(String value) {

    private static final Pattern FORMAT = Pattern.compile("^\\+55[1-9][1-9]9\\d{8}$");

    public MobilePhone {
        Objects.requireNonNull(value, "mobilePhone");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_MOBILE_PHONE", "Celular inválido");
        }
    }

    /** Aceita formatos usuais: {@code (11) 91234-5678}, {@code 11912345678}, {@code +55 11 91234-5678}. */
    public static MobilePhone of(String raw) {
        if (raw == null) {
            throw new InvalidValueException("INVALID_MOBILE_PHONE", "Celular inválido");
        }
        var digits = raw.replaceAll("\\D", "");
        if (digits.length() == 11) {
            digits = "55" + digits;
        }
        return new MobilePhone("+" + digits);
    }

    public String masked() {
        return value.substring(0, 5) + "*****" + value.substring(value.length() - 4);
    }

    @Override
    public String toString() {
        return masked();
    }
}
