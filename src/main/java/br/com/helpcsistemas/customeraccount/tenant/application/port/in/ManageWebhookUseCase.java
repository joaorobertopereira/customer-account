package br.com.helpcsistemas.customeraccount.tenant.application.port.in;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.application.query.DeliveryView;
import br.com.helpcsistemas.customeraccount.tenant.application.query.WebhookConfigurationView;
import java.util.List;
import java.util.Optional;

public interface ManageWebhookUseCase {

    /** Cria/atualiza o webhook. Gera segredo na primeira configuração ou quando {@code rotateSecret}. */
    WebhookConfigurationView configure(TenantId tenant, String url, boolean rotateSecret, String actor);

    Optional<WebhookConfigurationView> find(TenantId tenant);

    void disable(TenantId tenant, String actor);

    List<DeliveryView> recentDeliveries(TenantId tenant, int limit);
}
