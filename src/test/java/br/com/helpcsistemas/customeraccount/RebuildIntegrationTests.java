package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.helpcsistemas.customeraccount.consent.adapter.codec.ConsentEventCodec;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentRevoked;
import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.SubscriptionAdmin;
import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Reconstrução controlada de projeção: descarta o read model e reprojeta todo o histórico do event store. */
@Testcontainers(disabledWithoutDocker = true)
class RebuildIntegrationTests extends IntegrationTestSupport {

    private static final String VIEWS = "customer_views";
    private static final String NOTIFICATIONS = "tenant_notifications";
    private static final String CHECKPOINTS = "eventstore_subscription_checkpoints";

    @Autowired
    CustomerRepositoryPort customers;

    @Autowired
    ConsentEventCodec consentCodec;

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
                })
                .anySatisfy(info -> {
                    assertThat(info.name()).isEqualTo("consent-views-v1");
                    assertThat(info.rebuildable()).isTrue();
                })
                .anySatisfy(info -> {
                    assertThat(info.name()).isEqualTo("consent-revocation-notifier-v1");
                    assertThat(info.rebuildable()).isFalse();
                });
        assertThatThrownBy(() -> subscriptions.rebuild("customer-erasure-v1"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> subscriptions.rebuild("consent-revocation-notifier-v1"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rebuildingConsentViewsDoesNotResendRevocationWebhooks() throws Exception {
        var tenant = "tenant-rebuild-consent";
        mvc.perform(put("/api/v1/tenant/webhook")
                        .with(tenantAdmin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://localhost:9/hook\"}"))
                .andExpect(status().isOk());
        var customerId = UUID.randomUUID();
        var stream = "consent-" + customerId + "-" + tenant;
        eventStore.append(
                stream,
                ExpectedRevision.noStream(),
                List.of(consentCodec.encode(
                        new ConsentRevoked(customerId, TenantId.of(tenant), "marketing", "motivo", Instant.now()))));
        var notificationId = stream + ":0";
        await().atMost(EVENTUAL).until(() -> mongo.findById(notificationId, Document.class, NOTIFICATIONS) != null);

        // Simula o registro já expirado pela TTL de 30 dias: nada mais deduplicaria um reenvio.
        mongo.remove(Query.query(Criteria.where("_id").is(notificationId)), NOTIFICATIONS);
        subscriptions.rebuild("consent-views-v1");

        await().atMost(EVENTUAL).until(() -> mongo.findById("consent-views-v1", Document.class, CHECKPOINTS) != null);
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(6))
                .until(() -> mongo.findById(notificationId, Document.class, NOTIFICATIONS) == null);
    }
}
