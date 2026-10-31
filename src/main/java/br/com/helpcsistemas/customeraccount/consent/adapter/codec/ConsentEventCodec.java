package br.com.helpcsistemas.customeraccount.consent.adapter.codec;

import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentGranted;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentRevoked;
import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ConsentEventCodec {
    private final JsonMapper json;
    private final PersonalDataCipher cipher;

    public ConsentEventCodec(JsonMapper json, PersonalDataCipher cipher) {
        this.json = json;
        this.cipher = cipher;
    }

    public NewEvent encode(ConsentEvent event) {
        var type = switch (event) {
            case ConsentGranted _ -> "ConsentGranted.v1";
            case ConsentRevoked _ -> "ConsentRevoked.v1";
        };
        return NewEvent.binary(
                type,
                cipher.encrypt(event.customerId().toString(), json.writeValueAsBytes(event)),
                Map.of(
                        "customerId",
                        event.customerId().toString(),
                        "tenantId",
                        event.tenantId().value()));
    }

    public ConsentEvent decode(StoredEvent event) {
        var payload = cipher.decrypt(event.metadata().get("customerId"), event.data());
        return switch (event.type()) {
            case "ConsentGranted.v1" -> json.readValue(payload, ConsentGranted.class);
            case "ConsentRevoked.v1" -> json.readValue(payload, ConsentRevoked.class);
            default -> throw new IllegalArgumentException("Evento de consentimento desconhecido: " + event.type());
        };
    }
}
