package br.com.helpcsistemas.customeraccount.tenant.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.NotificationQueuePort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.NotificationQueuePort.QueuedNotification;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.TenantEventsPublisherPort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookEndpointRepositoryPort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookEndpointRepositoryPort.StoredWebhook;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookSenderPort;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookTargetGuardPort;
import br.com.helpcsistemas.customeraccount.tenant.domain.model.DeliveryPolicy;
import br.com.helpcsistemas.customeraccount.tenant.domain.model.WebhookEndpoint;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantNotificationServiceTest {

    private static final TenantId TENANT = TenantId.of("tenant-a");

    private final NotificationQueuePort queue = mock(NotificationQueuePort.class);
    private final WebhookEndpointRepositoryPort endpoints = mock(WebhookEndpointRepositoryPort.class);
    private final WebhookSenderPort sender = mock(WebhookSenderPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);
    private final TenantNotificationService service = new TenantNotificationService(
            queue,
            endpoints,
            mock(WebhookTargetGuardPort.class),
            sender,
            mock(TenantEventsPublisherPort.class),
            new DeliveryPolicy(8),
            clock);

    @Test
    void unexpectedErrorOnOneItemDoesNotAbortTheBatch() {
        when(queue.claimDue(any(), anyInt())).thenReturn(List.of(notification("n-1"), notification("n-2")));
        when(endpoints.find(TENANT))
                .thenReturn(Optional.of(new StoredWebhook(
                        new WebhookEndpoint(TENANT, URI.create("https://tenant.example/hook"), true),
                        "whsec_test",
                        clock.instant(),
                        "admin")));
        when(sender.send(any(), anyMap(), anyString()))
                .thenThrow(new IllegalStateException("bug inesperado"))
                .thenReturn(204);

        var processed = service.dispatchDue(10);

        assertThat(processed).isEqualTo(2);
        verify(queue).reschedule(eq("n-1"), eq(1), isNull(), eq("UNEXPECTED_ERROR"), any(), any());
        verify(queue).markDelivered(eq("n-2"), eq(1), eq(204), any());
    }

    @Test
    void failureOutsideTheSendDoesNotAbortTheBatch() {
        when(queue.claimDue(any(), anyInt())).thenReturn(List.of(notification("n-1"), notification("n-2")));
        when(endpoints.find(TENANT))
                .thenThrow(new IllegalStateException("mongo indisponível"))
                .thenReturn(Optional.empty());

        assertThat(service.dispatchDue(10)).isEqualTo(2);
        verify(queue).markDiscarded(eq("n-2"), eq("WEBHOOK_NOT_CONFIGURED"), any());
    }

    private static QueuedNotification notification(String id) {
        return new QueuedNotification(
                id, TENANT, "verification.completed", "ref", "subject", "{}", 0, Instant.parse("2026-11-24T11:00:00Z"));
    }
}
