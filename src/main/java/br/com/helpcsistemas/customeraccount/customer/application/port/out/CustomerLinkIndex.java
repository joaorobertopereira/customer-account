package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CustomerLinkIndex {
    void pending(UUID customer, UUID request, Instant expiresAt);

    void remove(UUID request);

    List<ExpiredRequest> expired(Instant now);

    record ExpiredRequest(UUID customerId, UUID requestId) {}
}
