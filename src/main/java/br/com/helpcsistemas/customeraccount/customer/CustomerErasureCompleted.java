package br.com.helpcsistemas.customeraccount.customer;

import java.time.Instant;
import java.util.UUID;

/** Publicado após a destruição da chave do titular: outros módulos devem apagar seus dados derivados. */
public record CustomerErasureCompleted(UUID customerId, Instant occurredAt) {}
