package br.com.helpcsistemas.customeraccount.tenant;

public interface TenantNotifications {

    /**
     * Enfileira uma notificação para o webhook do tenant. Idempotente por {@code eventId}; ignorada se o tenant não
     * tiver webhook ativo (ele continua podendo consultar via API).
     */
    void enqueue(TenantNotification notification);
}
