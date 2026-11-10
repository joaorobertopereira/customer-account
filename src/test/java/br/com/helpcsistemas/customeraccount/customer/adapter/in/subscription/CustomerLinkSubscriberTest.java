package br.com.helpcsistemas.customeraccount.customer.adapter.in.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import br.com.helpcsistemas.customeraccount.customer.CustomerTenantUnlinked;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerEventCodec;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerLinkIndex;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkApproved;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerUnlinkedFromTenant;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.tenant.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.json.JsonMapper;

class CustomerLinkSubscriberTest {
    @Test
    void approvedAndUnlinkedEventsProduceWebhooksAndCleanupNotification() {
        var codec = mock(CustomerEventCodec.class);
        var index = mock(CustomerLinkIndex.class);
        var notifications = mock(TenantNotifications.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var subscriber = new CustomerLinkSubscriber(
                codec, index, notifications, publisher, JsonMapper.builder().build());
        var id = CustomerId.newId();
        var tenant = TenantId.of("tenant-a");
        var request = UUID.randomUUID();
        var now = Instant.parse("2026-01-15T12:00:00Z");
        var approved = new StoredEvent(
                "customer-" + id, 2, UUID.randomUUID(), "CustomerLinkApproved.v1", new byte[0], Map.of(), now);
        var unlinked = new StoredEvent(
                "customer-" + id, 4, UUID.randomUUID(), "CustomerUnlinkedFromTenant.v1", new byte[0], Map.of(), now);
        when(codec.decode(approved)).thenReturn(new CustomerLinkApproved(id, tenant, request, now));
        when(codec.decode(unlinked)).thenReturn(new CustomerUnlinkedFromTenant(id, tenant, now));
        subscriber.onEvent(approved);
        subscriber.onEvent(unlinked);
        verify(index).remove(request);
        verify(publisher).publishEvent(new CustomerTenantUnlinked(id.value(), tenant, now));
        var capture = ArgumentCaptor.forClass(TenantNotification.class);
        verify(notifications, times(2)).enqueue(capture.capture());
        assertThat(capture.getAllValues())
                .extracting(TenantNotification::eventType)
                .containsExactly("customer.link_approved", "customer.unlinked");
        assertThat(capture.getAllValues())
                .extracting(TenantNotification::eventId)
                .containsExactly("customer-" + id + ":2", "customer-" + id + ":4");
    }
}
