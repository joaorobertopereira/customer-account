package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param connectionString ex.: {@code kurrentdb://localhost:2113?tls=false}; em produção use TLS e credenciais.
 * @param subscriptionsEnabled permite separar instâncias de API (false) de instâncias de projeção (true).
 */
@Validated
@ConfigurationProperties("app.eventstore")
public record EventStoreProperties(
        @NotBlank String connectionString,
        boolean subscriptionsEnabled,
        @NotNull Duration resubscribeDelay,
        @NotNull Duration operationTimeout) {}
