package br.com.helpcsistemas.customeraccount.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipal;
import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipalConverter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class SubjectPrincipalConverterTest {

    private final SubjectPrincipalConverter converter = new SubjectPrincipalConverter();

    @Test
    void convertsOnlyValidatedTenantAndCustomerClaims() {
        var customerId = UUID.randomUUID();
        var principal = converter.convert(
                jwt().claim("customer_id", customerId.toString()).build());

        assertThat(principal).isEqualTo(new SubjectPrincipal(customerId, TenantId.of("tenant-a"), "subject-123"));
    }

    @Test
    void permitsAnUnlinkedSubjectButDoesNotInventCustomerId() {
        var principal = converter.convert(jwt().build());

        assertThat(principal.customerId()).isNull();
        assertThat(principal.isLinked()).isFalse();
    }

    @Test
    void rejectsMalformedCustomerOrTenantClaims() {
        assertThatThrownBy(() -> converter.convert(
                        jwt().claim("customer_id", "not-a-uuid").build()))
                .isInstanceOf(InvalidBearerTokenException.class);
        assertThatThrownBy(() -> converter.convert(Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .subject("subject-123")
                        .build()))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject-123")
                .claim("tenant_id", "tenant-a");
    }
}
