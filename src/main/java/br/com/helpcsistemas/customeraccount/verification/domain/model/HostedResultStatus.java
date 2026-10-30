package br.com.helpcsistemas.customeraccount.verification.domain.model;

/** Status neutro para os resultados enviados por provedores de verificação hospedada. */
public enum HostedResultStatus {
    COMPLETED,
    USER_FAILED,
    FRAUD_DETECTED,
    PROVIDER_ERROR,
    ABANDONED
}
