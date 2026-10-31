package br.com.helpcsistemas.customeraccount.verification.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Retorno normalizado de uma sessão hospedada; rawResponse permanece cifrado quando persistido. */
public record HostedResult(
        String sessionId,
        HostedResultStatus status,
        Integer score,
        List<String> reasonCodes,
        List<String> fraudSignals,
        String providerEventId,
        Instant occurredAt,
        String rawResponse) {

    public HostedResult {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId obrigatório");
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(occurredAt, "occurredAt");
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        fraudSignals = fraudSignals == null ? List.of() : List.copyOf(fraudSignals);
    }
}
