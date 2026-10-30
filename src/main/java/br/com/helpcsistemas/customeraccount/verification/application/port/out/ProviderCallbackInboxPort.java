package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Inbox cifrada e deduplicada para callbacks já autenticados. */
public interface ProviderCallbackInboxPort {

    boolean enqueue(ProviderId provider, String eventId, UUID customerId, byte[] payload, Instant receivedAt);

    List<Callback> claimDue(Instant now, int limit);

    void markProcessed(String id, Instant at);

    void markUnmatched(String id, Instant at);

    void retry(String id, int attempts, Instant nextAttemptAt, Instant at);

    void deleteByCustomer(UUID customerId);

    record Callback(String id, ProviderId provider, String eventId, byte[] payload, int attempts) {}
}
