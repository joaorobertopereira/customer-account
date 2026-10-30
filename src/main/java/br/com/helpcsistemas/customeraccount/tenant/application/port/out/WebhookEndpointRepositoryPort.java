package br.com.helpcsistemas.customeraccount.tenant.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.domain.model.WebhookEndpoint;
import java.time.Instant;
import java.util.Optional;

public interface WebhookEndpointRepositoryPort {

    /** O segredo volta decifrado; o adapter o persiste cifrado. */
    Optional<StoredWebhook> find(TenantId tenant);

    void save(WebhookEndpoint endpoint, String secret, String updatedBy, Instant at);

    record StoredWebhook(WebhookEndpoint endpoint, String secret, Instant updatedAt, String updatedBy) {

        @Override
        public String toString() {
            return "StoredWebhook[" + endpoint.tenantId() + "]";
        }
    }
}
