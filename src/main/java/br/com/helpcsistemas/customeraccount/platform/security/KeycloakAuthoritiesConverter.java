package br.com.helpcsistemas.customeraccount.platform.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Une escopos OAuth2 ({@code SCOPE_*}), roles de realm e roles do client que emitiu o token ({@code
 * resource_access.<azp>.roles}) em {@code ROLE_*}. Só o client {@code azp} conta: o admin de um tenant não ganha
 * a role ao logar pelo console de outro tenant.
 */
class KeycloakAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        var authorities = new ArrayList<>(scopes.convert(jwt));
        addRoles(authorities, jwt.getClaimAsMap("realm_access"));
        var resourceAccess = jwt.getClaimAsMap("resource_access");
        var client = jwt.getClaimAsString("azp");
        if (resourceAccess != null && client != null && resourceAccess.get(client) instanceof Map<?, ?> clientAccess) {
            addRoles(authorities, clientAccess);
        }
        return authorities;
    }

    private static void addRoles(List<GrantedAuthority> authorities, Map<?, ?> access) {
        if (access != null && access.get("roles") instanceof Collection<?> roles) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        }
    }
}
