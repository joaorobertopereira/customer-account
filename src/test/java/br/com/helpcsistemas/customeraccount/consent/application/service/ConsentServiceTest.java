package br.com.helpcsistemas.customeraccount.consent.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import br.com.helpcsistemas.customeraccount.consent.application.port.out.*;
import br.com.helpcsistemas.customeraccount.consent.domain.model.*;
import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.shared.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ConsentServiceTest {
    private final ConsentRepository ledgers = mock(ConsentRepository.class);
    private final PurposeRepository purposes = mock(PurposeRepository.class);
    private final CustomerDirectory customers = mock(CustomerDirectory.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
    private final TenantId tenant = TenantId.of("consent-test");
    private final UUID customer = UUID.randomUUID();
    private final ConsentService service = new ConsentService(ledgers, purposes, customers, clock);

    @Test
    void unconsentedFieldsStayHiddenAndNewPolicyInvalidatesOldConsent() {
        var purpose = new Purpose("marketing", "Marketing", Purpose.LegalBasis.CONSENT, Set.of("email"), "1");
        var contract = new Purpose("identity", "Contract", Purpose.LegalBasis.CONTRACT, Set.of("cpf"), "1");
        var ledger = ConsentLedger.rehydrate(customer, tenant, List.of(), -1);
        when(customers.linkedAt(tenant, customer)).thenReturn(Optional.of(clock.instant()));
        when(purposes.list(tenant)).thenReturn(List.of(purpose, contract));
        when(ledgers.load(customer, tenant)).thenReturn(ledger);
        assertThat(service.allowedFields(tenant, customer)).containsExactly("cpf");
        ledger.grant(purpose, new ConsentEvidence("web", "session", "a".repeat(64)), clock.instant(), clock);
        assertThat(service.allowedFields(tenant, customer)).containsExactlyInAnyOrder("cpf", "email");
        when(purposes.list(tenant))
                .thenReturn(List.of(
                        new Purpose("marketing", "New policy", Purpose.LegalBasis.CONSENT, Set.of("email"), "2"),
                        contract));
        assertThat(service.allowedFields(tenant, customer)).containsExactly("cpf");
        when(purposes.list(tenant)).thenReturn(List.of(purpose));
        when(customers.linkedAt(tenant, customer))
                .thenReturn(Optional.of(clock.instant().plusSeconds(10)));
        assertThat(service.allowedFields(tenant, customer)).isEmpty();
    }

    @Test
    void changingFieldsWithoutVersionIsRejected() {
        var old = new Purpose("contact", "Contact", Purpose.LegalBasis.CONSENT, Set.of("email"), "1");
        when(purposes.find(tenant, "contact")).thenReturn(Optional.of(old));
        assertThatThrownBy(() -> service.putPurpose(
                        tenant,
                        new Purpose(
                                "contact", "Contact", Purpose.LegalBasis.CONSENT, Set.of("email", "mobilePhone"), "1")))
                .isInstanceOf(BusinessRuleException.class);
        verify(purposes, never()).save(any(), any());
    }

    @Test
    void unlinkedTenantHasNoAccess() {
        when(customers.linkedAt(tenant, customer)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.allowedFields(tenant, customer)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(ledgers);
    }
}
