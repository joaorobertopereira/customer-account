package br.com.helpcsistemas.customeraccount.tenant.application.query;

import java.time.Instant;

public record DeliveryView(
        String eventId,
        String eventType,
        String reference,
        String status,
        int attempts,
        Integer lastHttpStatus,
        String lastError,
        Instant nextAttemptAt,
        Instant updatedAt) {}
