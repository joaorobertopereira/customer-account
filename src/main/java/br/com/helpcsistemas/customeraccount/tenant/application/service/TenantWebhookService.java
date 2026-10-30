package br.com.helpcsistemas.customeraccount.tenant.application.service;

import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.application.port.in.ManageWebhookUseCase;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.NotificationQueuePort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookEndpointRepositoryPort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookEndpointRepositoryPort.StoredWebhook;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookTargetGuardPort;
import br.com.helpcsistemas.customeraccount.tenant.application.query.DeliveryView;
import br.com.helpcsistemas.customeraccount.tenant.application.query.WebhookConfigurationView;
import br.com.helpcsistemas.customeraccount.tenant.domain.model.WebhookEndpoint;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class TenantWebhookService implements ManageWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(TenantWebhookService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    static final int MAX_DELIVERIES = 100;

    private final WebhookEndpointRepositoryPort endpoints;
    private final WebhookTargetGuardPort guard;
    private final NotificationQueuePort queue;
    private final Clock clock;

    TenantWebhookService(
            WebhookEndpointRepositoryPort endpoints,
            WebhookTargetGuardPort guard,
            NotificationQueuePort queue,
            Clock clock) {
        this.endpoints = endpoints;
        this.guard = guard;
        this.queue = queue;
        this.clock = clock;
    }

    @Override
    public WebhookConfigurationView configure(TenantId tenant, String url, boolean rotateSecret, String actor) {
        var endpoint = WebhookEndpoint.of(tenant, url);
        guard.ensureAllowed(endpoint.url());
        var existing = endpoints.find(tenant);
        var issueSecret = rotateSecret || existing.isEmpty();
        var secret = issueSecret ? newSecret() : existing.get().secret();
        var now = clock.instant();
        endpoints.save(endpoint, secret, actor, now);
        log.atInfo()
                .addKeyValue(
                        "audit.action", rotateSecret ? "tenant-webhook.secret-rotated" : "tenant-webhook.configured")
                .addKeyValue("tenant.id", tenant)
                .addKeyValue("webhook.host", endpoint.url().getHost())
                .addKeyValue("changed.by", actor)
                .log("Webhook do tenant configurado");
        return new WebhookConfigurationView(endpoint.url().toString(), true, issueSecret ? secret : null, now, actor);
    }

    @Override
    public Optional<WebhookConfigurationView> find(TenantId tenant) {
        return endpoints.find(tenant).map(TenantWebhookService::toView);
    }

    @Override
    public void disable(TenantId tenant, String actor) {
        var stored = endpoints
                .find(tenant)
                .orElseThrow(() -> new ResourceNotFoundException("WEBHOOK_NOT_FOUND", "Webhook não configurado"));
        endpoints.save(stored.endpoint().disabled(), stored.secret(), actor, clock.instant());
        log.atInfo()
                .addKeyValue("audit.action", "tenant-webhook.disabled")
                .addKeyValue("tenant.id", tenant)
                .addKeyValue("changed.by", actor)
                .log("Webhook do tenant desativado");
    }

    @Override
    public List<DeliveryView> recentDeliveries(TenantId tenant, int limit) {
        return queue.recent(tenant, Math.clamp(limit, 1, MAX_DELIVERIES));
    }

    private static WebhookConfigurationView toView(StoredWebhook stored) {
        return new WebhookConfigurationView(
                stored.endpoint().url().toString(),
                stored.endpoint().enabled(),
                null,
                stored.updatedAt(),
                stored.updatedBy());
    }

    private static String newSecret() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
