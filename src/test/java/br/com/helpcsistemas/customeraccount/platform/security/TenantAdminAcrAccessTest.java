package br.com.helpcsistemas.customeraccount.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class TenantAdminAcrAccessTest {

    @Test
    void requiresConfiguredAcrForTenantAdminRoutes() {
        var manager = SecurityConfig.tenantAdminAccess(List.of("2"));
        var request = new RequestAuthorizationContext(new MockHttpServletRequest());

        assertThat(manager.authorize(() -> admin("1"), request).isGranted()).isFalse();
        assertThat(manager.authorize(() -> admin("2"), request).isGranted()).isTrue();
        assertThat(manager.authorize(() -> admin(null), request).isGranted()).isFalse();
    }

    private static JwtAuthenticationToken admin(String acr) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("tenant-admin")
                .claim("tenant_id", "tenant-a");
        if (acr != null) {
            builder.claim("acr", acr);
        }
        return new JwtAuthenticationToken(
                builder.issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build(),
                List.of(new SimpleGrantedAuthority("ROLE_tenant-admin")));
    }
}
