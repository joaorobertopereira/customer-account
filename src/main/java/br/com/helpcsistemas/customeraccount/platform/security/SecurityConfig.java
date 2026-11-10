package br.com.helpcsistemas.customeraccount.platform.security;

import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipalConverter;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import tools.jackson.databind.json.JsonMapper;

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
    static final String SCOPE_ME_READ = "SCOPE_me:read";
    static final String SCOPE_ME_WRITE = "SCOPE_me:write";
    private static final String[] CUSTOMER_READ_SCOPES = {
        SCOPE_READ,
        "SCOPE_customer:read:identity",
        "SCOPE_customer:read:contact",
        "SCOPE_customer:read:address",
        "SCOPE_customer:read:full"
    };

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityProperties properties,
            MongoTemplate mongo,
            IdempotencyProperties idempotencyProperties,
            JsonMapper json,
            Clock clock)
            throws Exception {
        // API stateless com Bearer token: CSRF não se aplica.
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/v1/provider-callbacks/**")
                        .permitAll()
                        .requestMatchers("/dev/hosted-verification/**")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .hasAuthority(ROLE_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/link", "/api/v1/me/link/otp")
                        .hasAuthority(SCOPE_ME_WRITE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/me/**")
                        .access(subjectAccess(SCOPE_ME_READ, true))
                        .requestMatchers(HttpMethod.POST, "/api/v1/me/**")
                        .access(subjectAccess(SCOPE_ME_WRITE, true))
                        .requestMatchers(HttpMethod.PUT, "/api/v1/me/**")
                        .access(subjectAccess(SCOPE_ME_WRITE, true))
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/me/**")
                        .access(subjectAccess(SCOPE_ME_WRITE, true))
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/me/**")
                        .access(subjectAccess(SCOPE_ME_WRITE, true))
                        .requestMatchers("/api/v1/me/**")
                        .denyAll()
                        // Rotas específicas precisam preceder o matcher amplo de eliminação.
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customers/*/link")
                        .hasAuthority(SCOPE_WRITE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/*/consents")
                        .hasAnyAuthority(CUSTOMER_READ_SCOPES)
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers/*/consents")
                        .hasAuthority(SCOPE_WRITE)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/customers/*/consents/*")
                        .hasAuthority(SCOPE_WRITE)
                        .requestMatchers(HttpMethod.POST, "/api/v1/customers/search")
                        .hasAnyAuthority(CUSTOMER_READ_SCOPES)
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/*/verification-status")
                        .hasAuthority(SCOPE_VERIFICATION_READ)
                        .requestMatchers(HttpMethod.GET, CUSTOMERS)
                        .hasAnyAuthority(CUSTOMER_READ_SCOPES)
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
                .addFilterAfter(
                        new IdempotencyFilter(mongo, idempotencyProperties, json, clock), AuthorizationFilter.class)
                .build();
    }

    static AuthorizationManager<RequestAuthorizationContext> tenantAdminAccess(List<String> acrValues) {
        return (authentication, context) -> {
            var auth = authentication.get();
            var admin = auth != null
                    && auth.getAuthorities().stream().anyMatch(a -> ROLE_TENANT_ADMIN.equals(a.getAuthority()));
            var strongAuthentication =
                    acrValues.isEmpty() || (auth instanceof JwtAuthenticationToken jwt && hasAcr(jwt, acrValues));
            return new AuthorizationDecision(admin && strongAuthentication);
        };
    }

    private static boolean hasAcr(JwtAuthenticationToken authentication, List<String> acrValues) {
        var acr = authentication.getToken().getClaimAsString("acr");
        return acr != null && acrValues.contains(acr);
    }

    private static AuthorizationManager<RequestAuthorizationContext> subjectAccess(
            String requiredScope, boolean requireCustomerId) {
        return (authentication, context) -> {
            var auth = authentication.get();
            var hasScope = auth != null
                    && auth.getAuthorities().stream().anyMatch(a -> requiredScope.equals(a.getAuthority()));
            var hasCustomerId = !requireCustomerId
                    || (auth instanceof JwtAuthenticationToken token && validCustomerId(token.getToken()));
            return new AuthorizationDecision(hasScope && hasCustomerId);
        };
    }

    private static boolean validCustomerId(Jwt jwt) {
        var claim = jwt.getClaimAsString("customer_id");
        if (claim == null || claim.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(claim);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static org.springframework.core.convert.converter.Converter<
                    org.springframework.security.oauth2.jwt.Jwt, AbstractAuthenticationToken>
            jwtConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakAuthoritiesConverter());
        var subjectPrincipalConverter = new SubjectPrincipalConverter();
        return jwt -> new SubjectJwtAuthenticationToken(
                jwt, converter.convert(jwt).getAuthorities(), subjectPrincipalConverter.convert(jwt));
    }
}
