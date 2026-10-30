package br.com.helpcsistemas.customeraccount.eventsourcing;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento a ser gravado. {@code data} pode ser JSON ou binário (ex.: payload cifrado). Metadados nunca devem conter
 * dados pessoais em claro.
 */
public record NewEvent(UUID eventId, String type, byte[] data, boolean json, Map<String, String> metadata) {

    public NewEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(data, "data");
        metadata = Map.copyOf(metadata);
    }

    public static NewEvent json(String type, byte[] data, Map<String, String> metadata) {
        return new NewEvent(UUID.randomUUID(), type, data, true, metadata);
    }

    public static NewEvent binary(String type, byte[] data, Map<String, String> metadata) {
        return new NewEvent(UUID.randomUUID(), type, data, false, metadata);
    }
}
