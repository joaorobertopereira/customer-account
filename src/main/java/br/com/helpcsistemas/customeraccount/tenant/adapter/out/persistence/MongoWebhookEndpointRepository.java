package br.com.helpcsistemas.customeraccount.tenant.adapter.out.persistence;

import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookEndpointRepositoryPort;
import br.com.helpcsistemas.customeraccount.tenant.domain.model.WebhookEndpoint;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/** O segredo de assinatura fica cifrado (envelope encryption do módulo privacy), nunca em claro no banco. */
@Component
class MongoWebhookEndpointRepository implements WebhookEndpointRepositoryPort {

    static final String COLLECTION = "tenant_webhooks";

    private final MongoTemplate mongo;
    private final PersonalDataCipher cipher;

    MongoWebhookEndpointRepository(MongoTemplate mongo, PersonalDataCipher cipher) {
        this.mongo = mongo;
        this.cipher = cipher;
    }

    @Override
    public Optional<StoredWebhook> find(TenantId tenant) {
        return Optional.ofNullable(mongo.findById(tenant.value(), WebhookDocument.class, COLLECTION))
                .map(doc -> new StoredWebhook(
                        new WebhookEndpoint(tenant, URI.create(doc.url()), doc.enabled()),
                        new String(cipher.decrypt(subject(tenant), doc.encryptedSecret()), StandardCharsets.UTF_8),
                        doc.updatedAt(),
                        doc.updatedBy()));
    }

    @Override
    public void save(WebhookEndpoint endpoint, String secret, String updatedBy, Instant at) {
        mongo.save(
                new WebhookDocument(
                        endpoint.tenantId().value(),
                        endpoint.url().toString(),
                        cipher.encrypt(subject(endpoint.tenantId()), secret.getBytes(StandardCharsets.UTF_8)),
                        endpoint.enabled(),
                        at,
                        updatedBy),
                COLLECTION);
    }

    private static String subject(TenantId tenant) {
        return "tenant-webhook:" + tenant.value();
    }

    record WebhookDocument(
            @Id String id, String url, byte[] encryptedSecret, boolean enabled, Instant updatedAt, String updatedBy) {}
}
