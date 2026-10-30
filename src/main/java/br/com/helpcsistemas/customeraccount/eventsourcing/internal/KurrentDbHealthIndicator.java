package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import io.kurrent.dbclient.KurrentDBClient;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("kurrentdb")
class KurrentDbHealthIndicator implements HealthIndicator {

    private final KurrentDBClient client;

    KurrentDbHealthIndicator(KurrentDBClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            var version = client.getServerVersion().get(2, TimeUnit.SECONDS);
            return Health.up()
                    .withDetail("version", version.map(Object::toString).orElse("unknown"))
                    .build();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Health.down(ex).build();
        } catch (ExecutionException | TimeoutException ex) {
            return Health.down(ex).build();
        }
    }
}
