package br.com.helpcsistemas.customeraccount.verification.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Retorno normalizado do provedor.
 *
 * @param rawResponse resposta original (auditoria); persistida cifrada e nunca devolvida ao tenant
 */
public record ProviderResult(
        ProviderDecision decision,
        Integer score,
        List<String> reasonCodes,
        String transactionId,
        long latencyMs,
        String rawResponse) {

    public ProviderResult {
        Objects.requireNonNull(decision, "decision");
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public ProviderResult withLatency(long millis) {
        return new ProviderResult(decision, score, reasonCodes, transactionId, millis, rawResponse);
    }

    @Override
    public String toString() {
        return "ProviderResult[decision=" + decision + ", score=" + score + ", transactionId=" + transactionId + "]";
    }
}
