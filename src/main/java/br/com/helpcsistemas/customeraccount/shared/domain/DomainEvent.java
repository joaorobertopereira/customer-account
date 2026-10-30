package br.com.helpcsistemas.customeraccount.shared.domain;

import java.time.Instant;

/** Fato de negócio imutável, já ocorrido. */
public interface DomainEvent {

    Instant occurredAt();
}
