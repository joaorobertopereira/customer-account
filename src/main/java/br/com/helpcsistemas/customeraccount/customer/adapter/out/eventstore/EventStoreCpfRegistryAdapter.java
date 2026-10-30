package br.com.helpcsistemas.customeraccount.customer.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CpfRegistryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
import br.com.helpcsistemas.customeraccount.privacy.BlindIndex;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Unicidade de CPF via stream de reserva {@code customer_cpf-<HMAC(cpf)>}. A gravação com revisão esperada torna a
 * reserva atômica mesmo com várias instâncias concorrendo. O nome do stream não expõe o CPF (blind index).
 */
@Component
class EventStoreCpfRegistryAdapter implements CpfRegistryPort {

    private static final Logger log = LoggerFactory.getLogger(EventStoreCpfRegistryAdapter.class);

    static final String STREAM_PREFIX = "customer_cpf-";
    static final String RESERVED = "CpfReserved.v1";
    static final String RELEASED = "CpfReleased.v1";
    private static final String INDEX_PURPOSE = "cpf";

    private final EventStore eventStore;
    private final BlindIndex blindIndex;

    EventStoreCpfRegistryAdapter(EventStore eventStore, BlindIndex blindIndex) {
        this.eventStore = eventStore;
        this.blindIndex = blindIndex;
    }

    @Override
    public boolean reserve(Cpf cpf, CustomerId owner) {
        var stream = streamOf(cpf);
        var last = eventStore.readLastEvent(stream);
        if (last.isPresent() && RESERVED.equals(last.get().type())) {
            return false;
        }
        var expected = last.map(e -> ExpectedRevision.of(e.revision())).orElseGet(ExpectedRevision::noStream);
        try {
            eventStore.append(stream, expected, List.of(event(RESERVED, owner)));
            log.atDebug().addKeyValue("customer.id", owner).log("CPF reservado");
            return true;
        } catch (StreamRevisionConflictException ex) {
            log.atWarn().addKeyValue("customer.id", owner).log("Corrida na reserva de CPF: outra requisição venceu");
            return false;
        }
    }

    @Override
    public void release(Cpf cpf, CustomerId owner) {
        var stream = streamOf(cpf);
        var last = eventStore.readLastEvent(stream);
        if (last.isEmpty() || !RESERVED.equals(last.get().type()) || !ownedBy(last.get(), owner)) {
            return;
        }
        try {
            eventStore.append(stream, ExpectedRevision.of(last.get().revision()), List.of(event(RELEASED, owner)));
            log.atInfo().addKeyValue("customer.id", owner).log("Reserva de CPF liberada");
        } catch (StreamRevisionConflictException ex) {
            log.atWarn().addKeyValue("customer.id", owner).log("Reserva de CPF alterada concorrentemente");
        }
    }

    private String streamOf(Cpf cpf) {
        return STREAM_PREFIX + blindIndex.compute(INDEX_PURPOSE, cpf.value());
    }

    private static NewEvent event(String type, CustomerId owner) {
        var payload = "{\"customerId\":\"" + owner.value() + "\"}";
        return NewEvent.json(type, payload.getBytes(StandardCharsets.UTF_8), Map.of("customerId", owner.toString()));
    }

    private static boolean ownedBy(StoredEvent event, CustomerId owner) {
        return owner.toString().equals(event.metadata().get("customerId"));
    }
}
