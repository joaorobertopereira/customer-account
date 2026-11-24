package br.com.helpcsistemas.customeraccount.consent.adapter.in.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import br.com.helpcsistemas.customeraccount.consent.adapter.codec.ConsentEventCodec;
import br.com.helpcsistemas.customeraccount.consent.application.port.out.*;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentRevoked;
import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class ConsentSubscriberTest {

    private final ConsentEventCodec codec = mock(ConsentEventCodec.class);
    private final UUID id = UUID.randomUUID();
    private final TenantId tenant = TenantId.of("tenant-a");
    private final Instant now = Instant.parse("2026-01-15T12:00:00Z");
    private final StoredEvent stored = new StoredEvent(
            "consent-" + id + "-tenant-a", 1, UUID.randomUUID(), "ConsentRevoked.v1", new byte[0], Map.of(), now);

    @Test
    void revokedEventRemovesStaleProjectionWithoutExternalEffects() {
        var ledgers = mock(ConsentRepository.class);
        var views = mock(ConsentProjection.class);
        var customers = mock(CustomerDirectory.class);
        var subscriber = new ConsentSubscriber(codec, ledgers, views, customers);
        when(codec.decode(stored)).thenReturn(new ConsentRevoked(id, tenant, "marketing", "private reason", now));

        subscriber.onEvent(stored);

        verify(views).remove(id, tenant);
        verifyNoInteractions(ledgers);
        assertThat(subscriber.rebuildable()).isTrue();
    }

    @Test
    void notifierQueuesDeterministicWebhookAndIsNotRebuildable() {
        var notifications = mock(TenantNotifications.class);
        var notifier = new ConsentRevocationNotifier(
                codec, notifications, JsonMapper.builder().build());
        when(codec.decode(stored)).thenReturn(new ConsentRevoked(id, tenant, "marketing", "private reason", now));

        notifier.onEvent(stored);

        var capture = ArgumentCaptor.forClass(TenantNotification.class);
        verify(notifications).enqueue(capture.capture());
        var event = capture.getValue();
        assertThat(event.eventType()).isEqualTo("consent.revoked");
        assertThat(event.tenantId()).isEqualTo(tenant);
        assertThat(event.eventId()).isEqualTo(stored.stream() + ":1");
        assertThat(event.payloadJson()).contains("\"purpose\":\"marketing\"").doesNotContain("private reason");
        assertThat(notifier.rebuildable()).isFalse();
        assertThat(notifier.inheritsCheckpointFrom()).contains(ConsentSubscriber.NAME);
    }

    @Test
    void notifierIgnoresOtherConsentEvents() {
        var notifications = mock(TenantNotifications.class);
        var notifier = new ConsentRevocationNotifier(
                codec, notifications, JsonMapper.builder().build());
        var granted = new StoredEvent(
                "consent-" + id + "-tenant-a", 0, UUID.randomUUID(), "ConsentGranted.v1", new byte[0], Map.of(), now);

        notifier.onEvent(granted);

        verifyNoInteractions(codec, notifications);
    }
}
