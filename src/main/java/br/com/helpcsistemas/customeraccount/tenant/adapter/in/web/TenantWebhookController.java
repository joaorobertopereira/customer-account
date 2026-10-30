package br.com.helpcsistemas.customeraccount.tenant.adapter.in.web;

import br.com.helpcsistemas.customeraccount.shared.domain.Actor;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.application.port.in.ManageWebhookUseCase;
import br.com.helpcsistemas.customeraccount.tenant.application.query.DeliveryView;
import br.com.helpcsistemas.customeraccount.tenant.application.query.WebhookConfigurationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administração do webhook do tenant (role {@code tenant-admin}). O segredo é exibido só ao ser gerado. */
@RestController
@RequestMapping("/api/v1/tenant/webhook")
@Tag(name = "Administração do tenant - webhook")
class TenantWebhookController {

    private final ManageWebhookUseCase webhooks;

    TenantWebhookController(ManageWebhookUseCase webhooks) {
        this.webhooks = webhooks;
    }

    @GetMapping
    @Operation(summary = "Webhook configurado (sem o segredo)")
    WebhookConfigurationView find(@Parameter(hidden = true) TenantId tenant) {
        return webhooks.find(tenant)
                .orElseThrow(() -> new ResourceNotFoundException("WEBHOOK_NOT_FOUND", "Webhook não configurado"));
    }

    @PutMapping
    @Operation(summary = "Configura o webhook; gera o segredo HMAC na primeira vez ou com rotateSecret=true")
    WebhookConfigurationView configure(
            @Parameter(hidden = true) TenantId tenant,
            @Parameter(hidden = true) Actor actor,
            @Valid @RequestBody ConfigureWebhookRequest request) {
        return webhooks.configure(tenant, request.url(), Boolean.TRUE.equals(request.rotateSecret()), actor.id());
    }

    @DeleteMapping
    @Operation(summary = "Desativa o webhook (o tenant continua consultando resultados pela API)")
    ResponseEntity<Void> disable(@Parameter(hidden = true) TenantId tenant, @Parameter(hidden = true) Actor actor) {
        webhooks.disable(tenant, actor.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Entregas recentes (status, tentativas, último erro)")
    List<DeliveryView> deliveries(
            @Parameter(hidden = true) TenantId tenant, @RequestParam(defaultValue = "20") int limit) {
        return webhooks.recentDeliveries(tenant, limit);
    }

    record ConfigureWebhookRequest(
            @NotBlank @Size(max = 2048) String url, Boolean rotateSecret) {}
}
