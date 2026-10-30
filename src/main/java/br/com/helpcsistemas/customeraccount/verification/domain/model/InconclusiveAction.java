package br.com.helpcsistemas.customeraccount.verification.domain.model;

/** O que fazer quando o provedor não consegue concluir (ex.: imagem de baixa qualidade). */
public enum InconclusiveAction {
    REJECT,
    MANUAL_REVIEW
}
