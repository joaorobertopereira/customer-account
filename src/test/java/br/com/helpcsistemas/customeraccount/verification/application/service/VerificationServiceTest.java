package br.com.helpcsistemas.customeraccount.verification.application.service;

import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.FALLBACK;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.PRIMARY;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.TENANT;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.otpRule;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.result;
import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.rule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderDescriptor;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderUnavailableException;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationPolicyRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProcessRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProviderGateway;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationSubject;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationPolicyEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.TenantVerificationPolicy;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationOutcome;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationServiceTest {

    private final InMemoryPolicies policies = new InMemoryPolicies();
    private final InMemoryProcesses processes = new InMemoryProcesses();
    private final FakeGateway gateway = new FakeGateway();
    private final UUID customerId = UUID.randomUUID();
    private final VerificationService service = new VerificationService(
            policies,
            processes,
            (tenant, id) -> id.equals(customerId)
                    ? Optional.of(new VerificationSubject(
                            id, "52998224725", "Maria Souza", LocalDate.of(1990, 1, 1), "m@x.com", "+5511912345678"))
                    : Optional.empty(),
            gateway,
            CLOCK);
    private final VerificationPolicyService policyService = new VerificationPolicyService(policies, gateway, CLOCK);

    @Test
    void policyChangeTakesEffectOnTheNextCall() {
        gateway.respond(PRIMARY, result(ProviderDecision.APPROVED, 90));
        gateway.respond(FALLBACK, result(ProviderDecision.REJECTED, 10));
        policyService.configure(TENANT, rule(PRIMARY, null), "admin", "inicial");

        var first = service.request(TENANT, customerId, VerificationType.FACE_MATCH, "app");
        policyService.configure(TENANT, rule(FALLBACK, null), "admin", "troca");
        var second = service.request(TENANT, customerId, VerificationType.FACE_MATCH, "app");

        assertThat(first.provider()).isEqualTo(PRIMARY);
        assertThat(first.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThat(second.provider()).isEqualTo(FALLBACK);
        assertThat(second.policyVersion()).isEqualTo(first.policyVersion() + 1);
        assertThat(gateway.calls).containsExactly(PRIMARY, FALLBACK);
    }

    @Test
    void failsOverAutomaticallyAndPersistsTheWholeProcess() {
        gateway.fail(PRIMARY, "TIMEOUT");
        gateway.respond(FALLBACK, result(ProviderDecision.APPROVED, 95));
        policyService.configure(TENANT, rule(PRIMARY, FALLBACK), "admin", "inicial");

        var view = service.request(TENANT, customerId, VerificationType.FACE_MATCH, "app");

        assertThat(view.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThat(view.failedOver()).isTrue();
        assertThat(view.failures()).extracting(f -> f.errorCode()).containsExactly("TIMEOUT");
        var stored = processes.findById(view.id()).orElseThrow();
        assertThat(stored.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThat(stored.version()).isEqualTo(view.version());
    }

    @Test
    void requiresConfiguredPolicyAndLinkedCustomer() {
        assertThatThrownBy(() -> service.request(TENANT, customerId, VerificationType.LIVENESS, "app"))
                .isInstanceOf(BusinessRuleException.class);

        gateway.respond(PRIMARY, result(ProviderDecision.APPROVED, 90));
        policyService.configure(TENANT, rule(PRIMARY, null), "admin", "inicial");
        assertThatThrownBy(() -> service.request(TENANT, UUID.randomUUID(), VerificationType.FACE_MATCH, "app"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsProvidersNotAvailableOnThePlatform() {
        assertThatThrownBy(() -> policyService.configure(TENANT, rule(ProviderId.of("desconhecido"), null), "a", "r"))
                .isInstanceOf(InvalidValueException.class);
    }

    private VerificationView requestOtp() {
        gateway.respond(PRIMARY, result(ProviderDecision.CHALLENGE_SENT, null));
        policyService.configure(TENANT, otpRule(PRIMARY, null), "admin", "inicial");
        return service.request(TENANT, customerId, VerificationType.EMAIL_OWNERSHIP, "app");
    }

    @Test
    void confirmsChallengeWithTheHolderAnswer() {
        var pending = requestOtp();
        assertThat(pending.status()).isEqualTo(VerificationStatus.AWAITING_CONFIRMATION);

        var wrong = service.confirm(TENANT, pending.id(), "000000");
        var right = service.confirm(TENANT, pending.id(), "123456");

        assertThat(wrong.challengeAttempts()).isEqualTo(1);
        assertThat(right.outcome()).isEqualTo(VerificationOutcome.APPROVED);
        assertThatThrownBy(() -> service.confirm(TENANT, pending.id(), "123456"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("VERIFICATION_NOT_AWAITING_CONFIRMATION");
    }

    @Test
    void confirmationIsScopedToTheTenant() {
        var pending = requestOtp();

        assertThatThrownBy(() -> service.confirm(TenantId.of("tenant-b"), pending.id(), "123456"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void providerOutageDuringConfirmationKeepsTheChallengeOpen() {
        var pending = requestOtp();
        gateway.confirmUnavailable = true;

        assertThatThrownBy(() -> service.confirm(TENANT, pending.id(), "123456"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("PROVIDER_TEMPORARILY_UNAVAILABLE");
        var stored = processes.findById(pending.id()).orElseThrow();
        assertThat(stored.status()).isEqualTo(VerificationStatus.AWAITING_CONFIRMATION);
        assertThat(stored.failures()).extracting(f -> f.errorCode()).containsExactly("TIMEOUT");
    }

    static final class InMemoryPolicies implements VerificationPolicyRepositoryPort {

        private final Map<TenantId, List<VerificationPolicyEvent>> streams = new HashMap<>();

        @Override
        public TenantVerificationPolicy load(TenantId tenant) {
            var history = streams.getOrDefault(tenant, List.of());
            return history.isEmpty()
                    ? TenantVerificationPolicy.empty(tenant)
                    : TenantVerificationPolicy.rehydrate(tenant, history, history.size() - 1);
        }

        @Override
        public void save(TenantVerificationPolicy policy) {
            var stream = streams.computeIfAbsent(policy.tenantId(), t -> new ArrayList<>());
            stream.addAll(policy.pendingEvents());
            policy.markCommitted(stream.size() - 1);
        }

        @Override
        public List<VerificationRuleConfigured> history(TenantId tenant) {
            return streams.getOrDefault(tenant, List.of()).stream()
                    .map(VerificationRuleConfigured.class::cast)
                    .toList();
        }
    }

    static final class InMemoryProcesses implements VerificationProcessRepositoryPort {

        private final Map<VerificationId, List<VerificationEvent>> streams = new HashMap<>();

        @Override
        public Optional<VerificationProcess> findById(VerificationId id) {
            var history = streams.get(id);
            return history == null
                    ? Optional.empty()
                    : Optional.of(VerificationProcess.rehydrate(history, history.size() - 1));
        }

        @Override
        public void save(VerificationProcess process) {
            var stream = streams.computeIfAbsent(process.id(), id -> new ArrayList<>());
            stream.addAll(process.pendingEvents());
            process.markCommitted(stream.size() - 1);
        }
    }

    static final class FakeGateway implements VerificationProviderGateway {

        private final Map<ProviderId, ProviderResult> results = new HashMap<>();
        private final Map<ProviderId, String> failures = new HashMap<>();
        final List<ProviderId> calls = new ArrayList<>();
        String expectedAnswer = "123456";
        boolean confirmUnavailable;

        void respond(ProviderId provider, ProviderResult result) {
            results.put(provider, result);
        }

        void fail(ProviderId provider, String errorCode) {
            failures.put(provider, errorCode);
        }

        @Override
        public ProviderResult verify(ProviderId provider, ProviderRequest request) {
            calls.add(provider);
            if (failures.containsKey(provider)) {
                throw new ProviderUnavailableException(failures.get(provider), 5, null);
            }
            return results.get(provider);
        }

        @Override
        public ProviderResult confirm(
                ProviderId provider, ProviderRequest request, String transactionId, String answer) {
            if (confirmUnavailable) {
                throw new ProviderUnavailableException("TIMEOUT", 5, null);
            }
            return result(expectedAnswer.equals(answer) ? ProviderDecision.APPROVED : ProviderDecision.REJECTED, null);
        }

        @Override
        public boolean supports(ProviderId provider, VerificationType type) {
            return results.containsKey(provider) || failures.containsKey(provider);
        }

        @Override
        public List<ProviderDescriptor> providers() {
            Set<VerificationType> all = EnumSet.allOf(VerificationType.class);
            return results.keySet().stream()
                    .map(id -> new ProviderDescriptor(id, all))
                    .toList();
        }
    }
}
