package br.com.helpcsistemas.customeraccount.tenant.adapter.out.http;

import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookSenderPort;
import br.com.helpcsistemas.customeraccount.tenant.config.TenantWebhookProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/** POST JSON para o tenant: sem seguir redirects (evita desvio para destinos internos) e com timeout curto. */
@Component
class JdkWebhookSender implements WebhookSenderPort {

    private final HttpClient http;
    private final TenantWebhookProperties properties;

    JdkWebhookSender(TenantWebhookProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public int send(URI url, Map<String, String> headers, String body) {
        var request = HttpRequest.newBuilder(url)
                .timeout(properties.timeout())
                .header("Content-Type", "application/json")
                .header("User-Agent", "customer-account-webhooks/1");
        headers.forEach(request::header);
        try {
            return http.send(
                            request.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                                    .build(),
                            HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        } catch (HttpTimeoutException ex) {
            throw new WebhookSendException("TIMEOUT", ex);
        } catch (IOException ex) {
            throw new WebhookSendException("CONNECTION_ERROR", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WebhookSendException("INTERRUPTED", ex);
        }
    }
}
