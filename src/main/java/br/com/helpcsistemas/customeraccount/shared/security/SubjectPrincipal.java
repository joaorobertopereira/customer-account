package br.com.helpcsistemas.customeraccount.shared.security;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Objects;
import java.util.UUID;

/** Identidade do titular derivada exclusivamente de claims validados do JWT. */
public record SubjectPrincipal(UUID customerId, TenantId tenantId, String subject) {

    public SubjectPrincipal {
        Objects.requireNonNull(tenantId, "tenantId");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject não pode ser vazio");
        }
    }

    public boolean isLinked() {
        return customerId != null;
    }
}
