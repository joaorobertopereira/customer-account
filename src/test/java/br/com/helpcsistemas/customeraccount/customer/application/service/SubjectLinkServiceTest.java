package br.com.helpcsistemas.customeraccount.customer.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.customer.CustomerSnapshot;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectBindingRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectIdentityAdminPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkChallengePort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkChallengePort.Challenge;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkCodeHasherPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkOtpDeliveryPort;
import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubjectLinkServiceTest {

    private static final TenantId TENANT = TenantId.of("tenant-a");
    private static final String EMAIL = "maria.souza@example.com";
    private static final String SUBJECT = "oidc-user-123";
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private CustomerDirectory customers;
    private SubjectLinkChallengePort challenges;
    private SubjectBindingRepositoryPort bindings;
    private SubjectIdentityAdminPort identityAdmin;
    private SubjectLinkOtpDeliveryPort delivery;
    private SubjectLinkCodeHasherPort codeHasher;
    private SubjectLinkService service;

    @BeforeEach
    void setUp() {
        customers = org.mockito.Mockito.mock(CustomerDirectory.class);
        challenges = org.mockito.Mockito.mock(SubjectLinkChallengePort.class);
        bindings = org.mockito.Mockito.mock(SubjectBindingRepositoryPort.class);
        identityAdmin = org.mockito.Mockito.mock(SubjectIdentityAdminPort.class);
        delivery = org.mockito.Mockito.mock(SubjectLinkOtpDeliveryPort.class);
        codeHasher = org.mockito.Mockito.mock(SubjectLinkCodeHasherPort.class);
        service = new SubjectLinkService(
                customers,
                challenges,
                bindings,
                identityAdmin,
                delivery,
                codeHasher,
                Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void requiresVerifiedJwtEmailBeforeSendingOtp() {
        assertThatThrownBy(() -> service.requestLink(principal(null), EMAIL, false))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("e-mail verificado");

        verify(delivery, never()).send(anyString(), anyString(), any());
    }

    @Test
    void deliversOtpOnlyToExistingTenantCustomerEmailAndBindsChallenge() {
        when(customers.findLinkedCustomerByEmail(TENANT, EMAIL)).thenReturn(Optional.of(customer()));
        when(challenges.findLatestForSubject(SUBJECT, TENANT, Instant.parse("2026-10-31T12:00:00Z")))
                .thenReturn(Optional.empty());
        when(codeHasher.hash(anyString(), eq(SUBJECT), eq(TENANT.value()), eq(CUSTOMER_ID), anyString()))
                .thenAnswer(invocation -> "hmac:" + invocation.getArgument(4, String.class));

        var challengeId = service.requestLink(principal(null), EMAIL, true);

        var saved = org.mockito.ArgumentCaptor.forClass(Challenge.class);
        verify(challenges).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(challengeId);
        assertThat(saved.getValue().subject()).isEqualTo(SUBJECT);
        assertThat(saved.getValue().tenant()).isEqualTo(TENANT);
        assertThat(saved.getValue().customerId()).isEqualTo(CUSTOMER_ID);

        var code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(delivery).send(eq(EMAIL), code.capture(), eq(SubjectLinkService.OTP_TTL));
        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(saved.getValue().codeHash()).isEqualTo("hmac:" + code.getValue());
    }

    @Test
    void challengeMustBelongToSameSubjectAndTenant() {
        when(challenges.find("challenge-1", SUBJECT, TENANT, Instant.parse("2026-10-31T12:00:00Z")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmLink(principal(null), EMAIL, true, "challenge-1", "123456"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inválido ou expirado");

        verify(customers, never()).findLinkedCustomerByEmail(any(), anyString());
        verify(identityAdmin, never()).setCustomerId(anyString(), anyString(), any());
    }

    @Test
    void anAlreadyBoundSubjectCannotBeReboundToAnotherCustomer() {
        var challenge = new Challenge(
                "challenge-2",
                SUBJECT,
                TENANT,
                CUSTOMER_ID,
                "hash",
                Instant.parse("2026-10-31T11:59:00Z"),
                Instant.parse("2026-10-31T12:10:00Z"),
                0);
        when(challenges.find("challenge-2", SUBJECT, TENANT, Instant.parse("2026-10-31T12:00:00Z")))
                .thenReturn(Optional.of(challenge));
        when(customers.findLinkedCustomerByEmail(TENANT, EMAIL)).thenReturn(Optional.of(customer()));
        when(codeHasher.hash("challenge-2", SUBJECT, TENANT.value(), CUSTOMER_ID, "123456"))
                .thenReturn("hash");
        when(challenges.consume("challenge-2", SUBJECT, TENANT, "hash", Instant.parse("2026-10-31T12:00:00Z")))
                .thenReturn(Optional.of(challenge));
        when(bindings.findCustomerId(SUBJECT)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.confirmLink(principal(null), EMAIL, true, "challenge-2", "123456"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("já está vinculada");

        verify(identityAdmin, never()).setCustomerId(anyString(), anyString(), any());
    }

    private static SubjectPrincipal principal(UUID customerId) {
        return new SubjectPrincipal(customerId, TENANT, SUBJECT);
    }

    private static CustomerSnapshot customer() {
        return new CustomerSnapshot(
                CUSTOMER_ID, "52998224725", "Maria da Silva Souza", LocalDate.of(1990, 5, 20), EMAIL, "+5511912345678");
    }
}
