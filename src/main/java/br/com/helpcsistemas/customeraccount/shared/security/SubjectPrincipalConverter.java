package br.com.helpcsistemas.customeraccount.shared.security;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/** Converte os claims autenticados de tenant e titular para o principal público da API. */
public class SubjectPrincipalConverter implements Converter<Jwt, SubjectPrincipal> {

    @Override
    public SubjectPrincipal convert(Jwt jwt) {
        try {
            var rawCustomerId = jwt.getClaimAsString("customer_id");
            var customerId = rawCustomerId == null || rawCustomerId.isBlank() ? null : UUID.fromString(rawCustomerId);
            return new SubjectPrincipal(customerId, TenantId.of(jwt.getClaimAsString("tenant_id")), jwt.getSubject());
        } catch (RuntimeException ex) {
            throw new InvalidBearerTokenException("Token de titular inválido");
        }
    }
}
