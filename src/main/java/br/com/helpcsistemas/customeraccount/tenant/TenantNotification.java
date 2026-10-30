package br.com.helpcsistemas.customeraccount.tenant;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Instant;
import java.util.Objects;

/**
 * @param reference identificador do recurso notificado (ex.: id da validação)
 * @param subjectId titular cujos dados estão no payload: a fila guarda o payload cifrado com a chave dele
 * @param payloadJson corpo JSON enviado ao tenant
 */
public record TenantNotification(
        TenantId tenantId,
        String eventId,
        String eventType,
        String reference,
        String subjectId,
        String payloadJson,
        Instant occurredAt) {

    public TenantNotification {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(payloadJson, "payloadJson");
    }

    @Override
    public String toString() {
        return "TenantNotification[" + eventType + ", " + eventId + "]";
    }
}
