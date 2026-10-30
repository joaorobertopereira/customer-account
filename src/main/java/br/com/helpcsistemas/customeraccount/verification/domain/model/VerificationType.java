package br.com.helpcsistemas.customeraccount.verification.domain.model;

public enum VerificationType {
    /** Comparação facial com base oficial ou documento. */
    FACE_MATCH,
    /** Prova de vida (anti-spoofing). */
    LIVENESS,
    DOCUMENT,
    FRAUD_SCORE,
    /** Posse do e-mail (código enviado e confirmado pelo titular). */
    EMAIL_OWNERSHIP,
    /** Qualidade do e-mail (existe, não é descartável). */
    EMAIL_QUALITY,
    /** Posse do celular (código por SMS confirmado pelo titular). */
    MOBILE_PHONE_OWNERSHIP,
    /** Risco da linha (tipo de linha, troca recente de SIM). */
    MOBILE_PHONE_RISK,
    /** Verificação hospedada de identidade, documento e biometria no provedor. */
    IDENTITY
}
