package br.com.helpcsistemas.customeraccount.verification.domain.model;

/** Resultado padronizado entregue ao tenant, independente do provedor. */
public enum VerificationOutcome {
    APPROVED,
    REJECTED,
    MANUAL_REVIEW,
    FRAUD_SUSPECTED,
    ERROR,
    /** Nenhum provedor (primário nem contingência) conseguiu responder. */
    FAILED,
    /** O titular não respondeu ao desafio dentro do prazo. */
    EXPIRED
}
