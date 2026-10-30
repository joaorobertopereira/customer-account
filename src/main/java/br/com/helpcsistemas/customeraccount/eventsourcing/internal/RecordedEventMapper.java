package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import io.kurrent.dbclient.RecordedEvent;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Converte eventos do client KurrentDB para a API neutra e (de)serializa metadados como JSON. */
@Component
class RecordedEventMapper {

    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {};
    private static final byte[] EMPTY = new byte[0];

    private final JsonMapper jsonMapper;

    RecordedEventMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    byte[] encodeMetadata(Map<String, String> metadata) {
        return metadata.isEmpty() ? EMPTY : jsonMapper.writeValueAsBytes(metadata);
    }

    Map<String, String> decodeMetadata(byte[] raw) {
        return raw == null || raw.length == 0 ? Map.of() : jsonMapper.readValue(raw, METADATA_TYPE);
    }

    StoredEvent toStored(RecordedEvent event) {
        return new StoredEvent(
                event.getStreamId(),
                event.getRevision(),
                event.getEventId(),
                event.getEventType(),
                event.getEventData(),
                decodeMetadata(event.getUserMetadata()),
                event.getCreated());
    }
}
