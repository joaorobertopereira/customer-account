package br.com.helpcsistemas.customeraccount.verification.domain.model;

import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.FALLBACK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.PRIMARY;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.TENANT;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import org.junit.jupiter.api.Test;

class TenantVerificationPolicyTest {

    @Test
    void recordsEveryChangeWithPreviousRule() {
        var policy = TenantVerificationPolicy.empty(TENANT);
        policy.configure(rule(PRIMARY, FALLBACK), "admin", "inicial", CLOCK);
        policy.configure(rule(FALLBACK, PRIMARY), "admin", "inversão", CLOCK);

        var changes = policy.pendingEvents().stream()
                .map(VerificationRuleConfigured.class::cast)
                .toList();
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).previous()).isNull();
        assertThat(changes.get(1).previous().primary()).isEqualTo(PRIMARY);
        assertThat(policy.ruleFor(VerificationType.FACE_MATCH).orElseThrow().primary())
                .isEqualTo(FALLBACK);
    }

    @Test
    void ignoresUnchangedRule() {
        var policy = TenantVerificationPolicy.empty(TENANT);
        policy.configure(rule(PRIMARY, FALLBACK), "admin", "inicial", CLOCK);
        policy.markCommitted(0);

        policy.configure(rule(PRIMARY, FALLBACK), "admin", "de novo", CLOCK);

        assertThat(policy.hasPendingEvents()).isFalse();
    }

    @Test
    void requiresReason() {
        var policy = TenantVerificationPolicy.empty(TENANT);

        assertThatThrownBy(() -> policy.configure(rule(PRIMARY, null), "admin", " ", CLOCK))
                .isInstanceOf(InvalidValueException.class);
    }

    @Test
    void rehydratesCurrentRules() {
        var policy = TenantVerificationPolicy.empty(TENANT);
        policy.configure(rule(PRIMARY, null), "admin", "inicial", CLOCK);

        var restored = TenantVerificationPolicy.rehydrate(TENANT, policy.pendingEvents(), 0);

        assertThat(restored.version()).isZero();
        assertThat(restored.rules()).containsKey(VerificationType.FACE_MATCH);
    }
}
