package br.com.helpcsistemas.customeraccount.platform.security;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Extrai o tenant do token autenticado. O tenant é sempre derivado da credencial, nunca de header/body. */
final class AuthenticatedTenant {

    static final String TENANT_CLAIM = "tenant_id";
    static final String CLIENT_CLAIM = "azp";

    private AuthenticatedTenant() {}

    static Optional<JwtAuthenticationToken> currentToken() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                ? Optional.of(token)
                : Optional.empty();
    }

    static Optional<String> rawTenant() {
        return currentToken().map(t -> t.getToken().getClaimAsString(TENANT_CLAIM));
    }

    static Optional<TenantId> current() {
        return rawTenant().map(TenantId::of);
    }
}
