package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.ParkedEvents;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Evento envenenado: após N falhas é estacionado, a projeção segue adiante e o evento pode ser reprocessado. */
@Testcontainers(disabledWithoutDocker = true)
class ParkingIntegrationTests extends IntegrationTestSupport {

    @Autowired
    EventStore eventStore;

    @Autowired
    ParkedEvents parked;

    @Test
    void poisonEventIsParkedThenReprocessed() {
        var stream = "poisontest-" + java.util.UUID.randomUUID();
        var payload = "{}".getBytes(StandardCharsets.UTF_8);
        eventStore.append(
                stream,
                ExpectedRevision.noStream(),
                List.of(NewEvent.json("Bad.v1", payload, Map.of()), NewEvent.json("Good.v1", payload, Map.of())));

        // o evento seguinte ao envenenado é processado: a projeção não ficou travada
        await().atMost(java.time.Duration.ofSeconds(60))
                .until(() -> PoisonTestSubscriber.PROCESSED.contains("Good.v1"));

        var entries =
                parked.list().stream().filter(p -> p.stream().equals(stream)).toList();
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().eventType()).isEqualTo("Bad.v1");
        assertThat(entries.getFirst().subscription()).isEqualTo("poison-test-v1");
        assertThat(entries.getFirst().attempts()).isEqualTo(5);

        PoisonTestSubscriber.FIXED.set(true);
        parked.reprocess(entries.getFirst().id());

        assertThat(PoisonTestSubscriber.PROCESSED).contains("Bad.v1");
        assertThat(parked.list()).noneMatch(p -> p.stream().equals(stream));
    }
}
