package br.com.helpcsistemas.customeraccount.tenant;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;

/** Publicado (evento Spring) quando o webhook do tenant confirmou o recebimento (HTTP 2xx). */
public record TenantNotificationDelivered(
        TenantId tenantId, String eventId, String eventType, String reference, int httpStatus, int attempts) {}
