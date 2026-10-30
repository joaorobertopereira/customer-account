package br.com.helpcsistemas.customeraccount.tenant.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/** Endpoint de webhook do tenant. Validação sintática; a checagem de destino (SSRF) é feita na infraestrutura. */
public record WebhookEndpoint(TenantId tenantId, URI url, boolean enabled) {

    private static final int MAX_LENGTH = 2048;

    public WebhookEndpoint {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(url, "url");
    }

    public static WebhookEndpoint of(TenantId tenantId, String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_LENGTH) {
            throw invalid();
        }
        try {
            var url = new URI(raw.strip());
            var scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("http"))
                    || url.getHost() == null
                    || url.getRawUserInfo() != null
                    || url.getRawFragment() != null) {
                throw invalid();
            }
            return new WebhookEndpoint(tenantId, url, true);
        } catch (URISyntaxException ex) {
            throw invalid();
        }
    }

    public WebhookEndpoint disabled() {
        return new WebhookEndpoint(tenantId, url, false);
    }

    private static InvalidValueException invalid() {
        return new InvalidValueException(
                "INVALID_WEBHOOK_URL", "URL de webhook inválida (use https, sem credenciais nem fragmento)");
    }
}
