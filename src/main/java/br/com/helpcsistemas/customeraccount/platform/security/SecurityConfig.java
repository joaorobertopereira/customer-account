package br.com.helpcsistemas.customeraccount.platform.security;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Resource server stateless. Autorização por escopo OAuth2 (concedido ao client do tenant no Keycloak) e
 * tenant obrigatório no token. Operações de manutenção exigem a role {@code admin}.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    static final String CUSTOMERS = "/api/v1/customers/**";
    static final String SCOPE_READ = "SCOPE_customer:read";
    static final String SCOPE_WRITE = "SCOPE_customer:write";
    static final String SCOPE_ERASE = "SCOPE_customer:erase";
    static final String ROLE_ADMIN = "ROLE_admin";
    static final String ROLE_TENANT_ADMIN = "ROLE_tenant-admin";
    static final String VERIFICATIONS = "/api/v1/verifications/**";
    static final String SCOPE_VERIFICATION_READ = "SCOPE_verification:read";
    static final String SCOPE_VERIFICATION_WRITE = "SCOPE_verification:write";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties) throws Exception {
        // API stateless com Bearer token: CSRF não se aplica.
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .hasAuthority(ROLE_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers/search")
                        .hasAuthority(SCOPE_READ)
                        .requestMatchers(HttpMethod.GET, CUSTOMERS)
                        .hasAuthority(SCOPE_READ)
                        .requestMatchers(HttpMethod.DELETE, CUSTOMERS)
                        .hasAuthority(SCOPE_ERASE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers", CUSTOMERS)
                        .hasAuthority(SCOPE_WRITE)
                        .requestMatchers(HttpMethod.PUT, CUSTOMERS)
                        .hasAuthority(SCOPE_WRITE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/verifications", VERIFICATIONS)
                        .hasAuthority(SCOPE_VERIFICATION_WRITE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/verifications", VERIFICATIONS)
                        .hasAuthority(SCOPE_VERIFICATION_READ)
                        // Administração do tenant: role tenant-admin do próprio console + login forte (acr).
                        .requestMatchers("/api/v1/tenant/**")
                        .access(tenantAdminAccess(properties.tenantAdminAcrValues()))
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
                .addFilterAfter(new TenantMdcFilter(), BearerTokenAuthenticationFilter.class)
                .build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> tenantAdminAccess(List<String> acrValues) {
        return (authentication, context) -> {
            var auth = authentication.get();
            var admin = auth != null
                    && auth.getAuthorities().stream().anyMatch(a -> ROLE_TENANT_ADMIN.equals(a.getAuthority()));
            var strongAuthentication = acrValues.isEmpty()
                    || (auth instanceof JwtAuthenticationToken jwt
                            && acrValues.contains(jwt.getToken().getClaimAsString("acr")));
            return new AuthorizationDecision(admin && strongAuthentication);
        };
    }

    private static JwtAuthenticationConverter jwtConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakAuthoritiesConverter());
        return converter;
    }
}
