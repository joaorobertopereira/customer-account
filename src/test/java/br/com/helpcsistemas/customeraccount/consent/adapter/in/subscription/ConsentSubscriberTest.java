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
    @Test
    void revokedEventRemovesStaleProjectionAndQueuesDeterministicWebhook() {
        var codec = mock(ConsentEventCodec.class);
        var ledgers = mock(ConsentRepository.class);
        var views = mock(ConsentProjection.class);
        var customers = mock(CustomerDirectory.class);
        var notifications = mock(TenantNotifications.class);
        var subscriber = new ConsentSubscriber(
                codec,
                ledgers,
                views,
                customers,
                notifications,
                JsonMapper.builder().build());
        var id = UUID.randomUUID();
        var tenant = TenantId.of("tenant-a");
        var now = Instant.parse("2026-01-15T12:00:00Z");
        var stored = new StoredEvent(
                "consent-" + id + "-tenant-a", 1, UUID.randomUUID(), "ConsentRevoked.v1", new byte[0], Map.of(), now);
        when(codec.decode(stored)).thenReturn(new ConsentRevoked(id, tenant, "marketing", "private reason", now));
        subscriber.onEvent(stored);
        verify(views).remove(id, tenant);
        verifyNoInteractions(ledgers);
        var capture = ArgumentCaptor.forClass(TenantNotification.class);
        verify(notifications).enqueue(capture.capture());
        var event = capture.getValue();
        assertThat(event.eventType()).isEqualTo("consent.revoked");
        assertThat(event.tenantId()).isEqualTo(tenant);
        assertThat(event.eventId()).isEqualTo(stored.stream() + ":1");
        assertThat(event.payloadJson()).contains("\"purpose\":\"marketing\"").doesNotContain("private reason");
    }
}
