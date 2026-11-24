package br.com.helpcsistemas.customeraccount;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Snapshots a cada 10 eventos com KurrentDB e MongoDB reais. */
@Testcontainers(disabledWithoutDocker = true)
class SnapshotIntegrationTests extends IntegrationTestSupport {

    private static final String COLLECTION = "eventstore_snapshots";

    @Autowired
    CustomerRepositoryPort customers;

    @Autowired
    EventStore eventStore;

    @Autowired
    MongoTemplate mongo;

    @Test
    void snapshotIsTakenEveryTenEventsAndUsedOnLoad() {
        var id = CustomerId.newId();
        var stream = "customer-" + id;

        var customer = CustomerFixtures.newCustomer(id); // 2 eventos (revisões 0..1)
        customers.save(customer);
        assertThat(mongo.findById(stream, Document.class, COLLECTION)).isNull();

        // 8 alterações -> 10 eventos: cruza o primeiro múltiplo de 10 (snapshot na revisão 9)
        for (int i = 1; i <= 8; i++) {
            var loaded = customers.findById(id).orElseThrow();
            loaded.changeEmail(TENANT_A, new Email("titular" + i + "@example.com"), CLOCK);
            customers.save(loaded);
            if (i < 8) {
                assertThat(mongo.findById(stream, Document.class, COLLECTION)).isNull();
            }
        }

        var snapshot = mongo.findById(stream, Document.class, COLLECTION);
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getLong("revision")).isEqualTo(9L);
        assertThat(snapshot.getString("subject")).isEqualTo(id.toString());
        // dados pessoais não ficam em claro no snapshot
        assertThat(new String(snapshot.get("data", Binary.class).getData())).doesNotContain("titular8@example.com");

        // novo evento após o snapshot: carga = snapshot + cauda de 1 evento
        var loaded = customers.findById(id).orElseThrow();
        assertThat(loaded.version()).isEqualTo(9L);
        loaded.changeEmail(TENANT_A, new Email("depois@example.com"), CLOCK);
        customers.save(loaded);
        assertThat(eventStore.readStream(stream, 10)).hasSize(1);

        var restored = customers.findById(id).orElseThrow();
        var fullReplayEmail = new Email("depois@example.com");
        assertThat(restored.version()).isEqualTo(10L);
        assertThat(restored.email()).isEqualTo(fullReplayEmail);
        assertThat(restored.tenants()).containsExactly(TENANT_A);
        assertThat(restored.cpf()).isEqualTo(CustomerFixtures.cpf());
        // snapshot permanece o da revisão 9 até o próximo múltiplo de 10
        assertThat(mongo.findById(stream, Document.class, COLLECTION).getLong("revision"))
                .isEqualTo(9L);
    }
}
