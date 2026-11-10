package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.eventsourcing.SubscriptionAdmin;
import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Reconstrução controlada de projeção: descarta o read model e reprojeta todo o histórico do event store. */
@Testcontainers(disabledWithoutDocker = true)
class RebuildIntegrationTests extends IntegrationTestSupport {

    private static final String VIEWS = "customer_views";

    @Autowired
    CustomerRepositoryPort customers;

    @Autowired
    SubscriptionAdmin subscriptions;

    @Autowired
    MongoTemplate mongo;

    @Test
    void rebuildRestoresReadModelFromEventHistory() {
        var id = CustomerId.newId();
        customers.save(CustomerFixtures.newCustomer(id));
        await().atMost(EVENTUAL).until(() -> mongo.findById(id.toString(), Document.class, VIEWS) != null);

        // simula read model corrompido/perdido
        mongo.remove(new org.springframework.data.mongodb.core.query.Query(), VIEWS);
        assertThat(mongo.findById(id.toString(), Document.class, VIEWS)).isNull();

        subscriptions.rebuild("customer-read-model-v1");

        await().atMost(EVENTUAL).until(() -> mongo.findById(id.toString(), Document.class, VIEWS) != null);
        assertThat(mongo.findById(id.toString(), Document.class, VIEWS).getLong("version"))
                .isEqualTo(1L);
    }

    @Test
    void subscriptionsWithExternalEffectsCannotBeRebuilt() {
        assertThat(subscriptions.list())
                .anySatisfy(info -> {
                    assertThat(info.name()).isEqualTo("customer-read-model-v1");
                    assertThat(info.rebuildable()).isTrue();
                })
                .anySatisfy(info -> {
                    assertThat(info.name()).isEqualTo("customer-erasure-v1");
                    assertThat(info.rebuildable()).isFalse();
                });
        assertThatThrownBy(() -> subscriptions.rebuild("customer-erasure-v1"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
