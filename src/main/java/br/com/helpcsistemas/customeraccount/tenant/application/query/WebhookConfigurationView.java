package br.com.helpcsistemas.customeraccount.tenant.application.query;

import java.time.Instant;

/** @param secret presente só quando acabou de ser gerado/rotacionado (exibido uma única vez) */
public record WebhookConfigurationView(
        String url, boolean enabled, String secret, Instant updatedAt, String updatedBy) {

    @Override
    public String toString() {
        return "WebhookConfigurationView[" + url + ", enabled=" + enabled + "]";
    }
}
