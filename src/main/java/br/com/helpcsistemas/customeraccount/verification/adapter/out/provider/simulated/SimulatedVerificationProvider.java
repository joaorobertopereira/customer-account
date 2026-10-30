package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.simulated;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvider;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Provedor de sandbox com comportamento fixo, para desenvolvimento e testes sem contrato com as empresas. */
class SimulatedVerificationProvider implements VerificationProvider {

    enum Behavior {
        APPROVE(ProviderDecision.APPROVED, 92, List.of()),
        REJECT(ProviderDecision.REJECTED, 18, List.of("FACE_MISMATCH")),
        INCONCLUSIVE(ProviderDecision.INCONCLUSIVE, 55, List.of("LOW_IMAGE_QUALITY")),
        UNAVAILABLE(null, null, List.of());

        private final ProviderDecision decision;
        private final Integer score;
        private final List<String> reasons;

        Behavior(ProviderDecision decision, Integer score, List<String> reasons) {
            this.decision = decision;
            this.score = score;
            this.reasons = reasons;
        }
    }

    private final ProviderId id;
    private final Behavior behavior;

    SimulatedVerificationProvider(String id, Behavior behavior) {
        this.id = new ProviderId(id);
        this.behavior = behavior;
    }

    @Override
    public ProviderId id() {
        return id;
    }

    @Override
    public Set<VerificationType> supportedTypes() {
        return EnumSet.allOf(VerificationType.class);
    }

    @Override
    public ProviderResult verify(ProviderRequest request) {
        if (behavior == Behavior.UNAVAILABLE) {
            throw new IllegalStateException("Simulated provider outage");
        }
        var transactionId = "sim-" + UUID.randomUUID();
        var raw = "{\"transactionId\":\"%s\",\"status\":\"%s\",\"score\":%d,\"provider\":\"%s\"}"
                .formatted(transactionId, behavior.decision, behavior.score, id);
        return new ProviderResult(behavior.decision, behavior.score, behavior.reasons, transactionId, 0, raw);
    }
}
