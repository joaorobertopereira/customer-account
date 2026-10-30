package br.com.helpcsistemas.customeraccount.eventsourcing;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Evento lido do event store. {@code revision} é a posição do evento dentro do stream (0..n). */
public record StoredEvent(
        String stream,
        long revision,
        UUID eventId,
        String type,
        byte[] data,
        Map<String, String> metadata,
        Instant recordedAt) {

    public StoredEvent {
        metadata = Map.copyOf(metadata);
    }
}
