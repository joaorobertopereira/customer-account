package br.com.helpcsistemas.customeraccount.customer.adapter.in.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerLinkIndex;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerLinkIndex.ExpiredRequest;
import br.com.helpcsistemas.customeraccount.customer.application.service.CustomerLinkService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerLinkExpirationSchedulerTest {

    private final CustomerLinkIndex index = mock(CustomerLinkIndex.class);
    private final CustomerLinkService links = mock(CustomerLinkService.class);
    private final CustomerLinkExpirationScheduler scheduler = new CustomerLinkExpirationScheduler(
            index, links, Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void failingRequestDoesNotAbortTheRestOfTheBatch() {
        var failing = new ExpiredRequest(UUID.randomUUID(), UUID.randomUUID());
        var healthy = new ExpiredRequest(UUID.randomUUID(), UUID.randomUUID());
        when(index.expired(any())).thenReturn(List.of(failing, healthy));
        doThrow(new IllegalStateException("event store indisponível"))
                .when(links)
                .expire(failing.customerId(), failing.requestId());

        scheduler.expire();

        verify(index, never()).remove(failing.requestId());
        verify(links).expire(healthy.customerId(), healthy.requestId());
        verify(index).remove(healthy.requestId());
    }
}
