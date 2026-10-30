package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import java.time.Instant;
import java.util.List;

/** Sessão hospedada do provedor; o URL é uma credencial e não deve ser registrado em logs. */
public record HostedSession(String sessionId, String url, Instant expiresAt, List<String> sharedFields) {

    public HostedSession {
        sharedFields = sharedFields == null ? List.of() : List.copyOf(sharedFields);
    }
}
