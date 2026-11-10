package br.com.helpcsistemas.customeraccount.consent.domain.model;

import static org.assertj.core.api.Assertions.*;

import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent;
import br.com.helpcsistemas.customeraccount.shared.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ConsentLedgerTest {
    private final UUID customer = UUID.randomUUID();
    private final TenantId tenant = TenantId.of("tenant-consent");
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T12:00:00Z"), ZoneOffset.UTC);
    private final Purpose purpose = new Purpose("contact", "Contact", Purpose.LegalBasis.CONSENT, Set.of("email"), "1");
    private final ConsentEvidence evidence = new ConsentEvidence("web", "session", "a".repeat(64));

    @Test
    void grantRevokeAndReplayPreserveEvidenceAndMembership() {
        var ledger = ConsentLedger.rehydrate(customer, tenant, List.of(), -1);
        ledger.grant(purpose, evidence, clock.instant(), clock);
        var event = ledger.granted().get("contact");
        assertThat(event.evidence()).isEqualTo(evidence);
        assertThat(event.linkedAt()).isEqualTo(clock.instant());
        var grantedHistory = ledger.pendingEvents();
        var replay = ConsentLedger.rehydrate(customer, tenant, grantedHistory, 0);
        assertThat(replay.granted()).containsKey("contact");
        replay.revoke("contact", "Holder requested", clock);
        var all = new ArrayList<ConsentEvent>(grantedHistory);
        all.addAll(replay.pendingEvents());
        assertThat(ConsentLedger.rehydrate(customer, tenant, all, 1).granted()).isEmpty();
        replay.revoke("contact", "Holder requested", clock);
        assertThat(replay.pendingEvents()).hasSize(1);
    }

    @Test
    void validatesLegalBasisFieldsAndEvidence() {
        var ledger = ConsentLedger.rehydrate(customer, tenant, List.of(), -1);
        assertThatThrownBy(() -> ledger.grant(
                        new Purpose("contract", "Contract", Purpose.LegalBasis.CONTRACT, Set.of("cpf"), "1"),
                        evidence,
                        clock.instant(),
                        clock))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Purpose("x", "Invalid", Purpose.LegalBasis.CONSENT, Set.of("secret"), "1"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new ConsentEvidence("web", "session", "invalid"))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> ledger.revoke("contact", "", clock)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void refusesHistoryFromAnotherTenant() {
        var ledger = ConsentLedger.rehydrate(customer, tenant, List.of(), -1);
        ledger.grant(purpose, evidence, clock.instant(), clock);
        assertThatThrownBy(() -> ConsentLedger.rehydrate(customer, TenantId.of("other"), ledger.pendingEvents(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
