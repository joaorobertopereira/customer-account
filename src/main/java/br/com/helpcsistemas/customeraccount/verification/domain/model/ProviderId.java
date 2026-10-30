package br.com.helpcsistemas.customeraccount.verification.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Identificador de provedor (ex.: {@code legitimuz}, {@code netrin}). Novos provedores não alteram o domínio. */
public record ProviderId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{1,39}$");

    public ProviderId {
        Objects.requireNonNull(value, "providerId");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_PROVIDER", "Provedor inválido");
        }
    }

    public static ProviderId of(String raw) {
        if (raw == null) {
            throw new InvalidValueException("INVALID_PROVIDER", "Provedor inválido");
        }
        return new ProviderId(raw.trim().toLowerCase(Locale.ROOT));
    }

    public static ProviderId ofNullable(String raw) {
        return raw == null || raw.isBlank() ? null : of(raw);
    }

    @Override
    public String toString() {
        return value;
    }
}
