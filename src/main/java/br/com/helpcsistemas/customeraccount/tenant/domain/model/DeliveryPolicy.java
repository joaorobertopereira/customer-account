package br.com.helpcsistemas.customeraccount.tenant.domain.model;

import java.time.Duration;
import java.util.List;

/** Retentativas com espera crescente (≈ 10h no total com 8 tentativas). */
public record DeliveryPolicy(int maxAttempts) {

    private static final List<Duration> SCHEDULE = List.of(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofHours(3),
            Duration.ofHours(6));

    public DeliveryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts deve ser >= 1");
        }
    }

    public boolean exhausted(int attempt) {
        return attempt >= maxAttempts;
    }

    public Duration delayAfter(int attempt) {
        return SCHEDULE.get(Math.min(Math.max(attempt, 1), SCHEDULE.size()) - 1);
    }
}
