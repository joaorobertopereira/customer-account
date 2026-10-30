package br.com.helpcsistemas.customeraccount.shared.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** Identificador da aplicação consumidora (tenant). Vem do claim {@code tenant_id} do token, nunca do cliente. */
public record TenantId(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");

    public TenantId {
        Objects.requireNonNull(value, "tenantId");
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidValueException("INVALID_TENANT_ID", "Identificador de tenant inválido");
        }
    }

    public static TenantId of(String value) {
        return new TenantId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
