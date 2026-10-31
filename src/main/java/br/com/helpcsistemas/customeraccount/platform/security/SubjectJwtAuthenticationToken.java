package br.com.helpcsistemas.customeraccount.platform.security;

import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipal;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class SubjectJwtAuthenticationToken extends JwtAuthenticationToken {

    private final SubjectPrincipal principal;

    SubjectJwtAuthenticationToken(
            Jwt jwt, Collection<? extends GrantedAuthority> authorities, SubjectPrincipal principal) {
        super(jwt, authorities, principal.subject());
        this.principal = principal;
    }

    SubjectPrincipal subjectPrincipal() {
        return principal;
    }
}
