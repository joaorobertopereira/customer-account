package br.com.helpcsistemas.customeraccount.verification.adapter.out.tenant;

import br.com.helpcsistemas.customeraccount.tenant.TenantNotification;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotifications;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationResultNotifierPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Contrato do webhook {@code verification.completed} (v1): mesmo resultado padronizado da API. O {@code eventId} é
 * determinístico, então reprocessamentos não geram entregas duplicadas.
 */
@Component
class TenantWebhookResultNotifier implements VerificationResultNotifierPort {

    static final String EVENT_TYPE = "verification.completed";

    private final TenantNotifications notifications;
    private final JsonMapper json;
    private final Clock clock;

    TenantWebhookResultNotifier(TenantNotifications notifications, JsonMapper json, Clock clock) {
        this.notifications = notifications;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void notifyResult(VerificationView v) {
        var eventId = EVENT_TYPE + ":" + v.id();
        var payload = new Envelope(
                eventId,
                EVENT_TYPE,
                v.concludedAt(),
                new Result(
                        v.id().toString(),
                        v.customerId(),
                        v.type().name(),
                        v.outcome() == null ? null : v.outcome().name(),
                        v.provider().value(),
                        v.score(),
                        v.reasonCodes(),
                        v.providerTransactionId(),
                        v.policyVersion(),
                        new Failover(
                                v.failedOver(),
                                v.failures().stream()
                                        .map(f -> new Failure(f.provider().value(), f.errorCode(), f.occurredAt()))
                                        .toList()),
                        v.requestedAt(),
                        v.concludedAt(),
                        v.validUntil()));
        notifications.enqueue(new TenantNotification(
                v.tenantId(),
                eventId,
                EVENT_TYPE,
                v.id().toString(),
                v.customerId().toString(),
                json.writeValueAsString(payload),
                v.concludedAt()));
    }

    @Override
    public void notifyValidityExpired(VerificationView v) {
        var eventType = "verification.validity_expired";
        var eventId = eventType + ":" + v.id();
        var occurredAt = clock.instant();
        var payload = new ValidityExpiredEnvelope(
                eventId,
                eventType,
                occurredAt,
                new ValidityExpiredResult(
                        v.id().toString(), v.customerId(), v.type().name(), v.validUntil()));
        notifications.enqueue(new TenantNotification(
                v.tenantId(),
                eventId,
                eventType,
                v.id().toString(),
                v.customerId().toString(),
                json.writeValueAsString(payload),
                occurredAt));
    }

    record Envelope(String eventId, String eventType, Instant occurredAt, Result data) {}

    record Result(
            String verificationId,
            UUID customerId,
            String type,
            String outcome,
            String provider,
            Integer score,
            List<String> reasonCodes,
            String providerTransactionId,
            long policyVersion,
            Failover failover,
            Instant requestedAt,
            Instant concludedAt,
            Instant validUntil) {}

    record Failover(boolean occurred, List<Failure> failures) {}

    record Failure(String provider, String errorCode, Instant occurredAt) {}

    record ValidityExpiredEnvelope(String eventId, String eventType, Instant occurredAt, ValidityExpiredResult data) {}

    record ValidityExpiredResult(String verificationId, UUID customerId, String type, Instant validUntil) {}
}
