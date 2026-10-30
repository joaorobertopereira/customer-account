package br.com.helpcsistemas.customeraccount.verification.domain.model;

/** Veredito do provedor já traduzido pela camada anticorrupção. */
public enum ProviderDecision {
    APPROVED,
    REJECTED,
    INCONCLUSIVE,
    /** O provedor enviou um desafio (ex.: código) e aguarda a resposta do titular. */
    CHALLENGE_SENT
}
