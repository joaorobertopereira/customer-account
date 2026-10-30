package br.com.helpcsistemas.customeraccount.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/** Coloca tenant e client OAuth2 no MDC: todo log da requisição (JSON) fica filtrável por tenant. */
class TenantMdcFilter extends OncePerRequestFilter {

    static final String MDC_TENANT = "auth.tenant";
    static final String MDC_CLIENT = "auth.client";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        AuthenticatedTenant.currentToken().ifPresent(token -> {
            putIfPresent(MDC_TENANT, token.getToken().getClaimAsString(AuthenticatedTenant.TENANT_CLAIM));
            putIfPresent(MDC_CLIENT, token.getToken().getClaimAsString(AuthenticatedTenant.CLIENT_CLAIM));
        });
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TENANT);
            MDC.remove(MDC_CLIENT);
        }
    }

    private static void putIfPresent(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }
}
