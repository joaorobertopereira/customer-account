package br.com.helpcsistemas.customeraccount.tenant;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;

/** Publicado (evento Spring) quando todas as tentativas de entrega se esgotaram. */
public record TenantNotificationFailed(
        TenantId tenantId, String eventId, String eventType, String reference, int attempts, Integer lastHttpStatus) {}
