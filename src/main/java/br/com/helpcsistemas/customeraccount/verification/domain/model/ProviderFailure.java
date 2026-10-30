package br.com.helpcsistemas.customeraccount.verification.domain.model;

import java.time.Instant;

/** Falha técnica de um provedor (indisponibilidade, timeout, circuito aberto). Não contém dados pessoais. */
public record ProviderFailure(ProviderId provider, String errorCode, long latencyMs, Instant occurredAt) {}
