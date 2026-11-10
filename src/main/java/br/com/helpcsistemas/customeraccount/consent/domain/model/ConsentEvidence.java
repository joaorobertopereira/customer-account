package br.com.helpcsistemas.customeraccount.consent.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;

public record ConsentEvidence(String channel, String sessionId, String textHash) {
    public ConsentEvidence {
        if (channel == null
                || !java.util.Set.of("app", "web", "presencial").contains(channel)
                || sessionId == null
                || sessionId.isBlank()
                || sessionId.length() > 200
                || textHash == null
                || !textHash.matches("[a-fA-F0-9]{64}")) {
            throw new InvalidValueException("INVALID_CONSENT_EVIDENCE", "Evidência de consentimento inválida");
        }
    }
}
