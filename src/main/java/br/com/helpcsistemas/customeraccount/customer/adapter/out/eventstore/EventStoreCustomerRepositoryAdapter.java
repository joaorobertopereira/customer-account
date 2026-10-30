package br.com.helpcsistemas.customeraccount.customer.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerEventCodec;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Repositório event-sourced: carrega por replay do stream e grava apenas os eventos novos. */
@Component
class EventStoreCustomerRepositoryAdapter implements CustomerRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(EventStoreCustomerRepositoryAdapter.class);

    private final EventStore eventStore;
    private final CustomerEventCodec codec;

    EventStoreCustomerRepositoryAdapter(EventStore eventStore, CustomerEventCodec codec) {
        this.eventStore = eventStore;
        this.codec = codec;
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        var stored = eventStore.readStream(codec.streamOf(id));
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        try {
            var history = stored.stream().map(codec::decode).toList();
            var customer = Customer.rehydrate(history, stored.getLast().revision());
            log.atDebug()
                    .addKeyValue("customer.id", id)
                    .addKeyValue("customer.version", customer.version())
                    .log("Agregado reconstruído a partir dos eventos");
            return Optional.of(customer);
        } catch (PersonalDataErasedException ex) {
            log.atDebug().addKeyValue("customer.id", id).log("Cliente com dados eliminados");
            return Optional.empty();
        }
    }

    @Override
    public void save(Customer customer) {
        if (!customer.hasPendingEvents()) {
            return;
        }
        var events = customer.pendingEvents().stream().map(codec::encode).toList();
        try {
            long revision =
                    eventStore.append(codec.streamOf(customer.id()), ExpectedRevision.of(customer.version()), events);
            customer.markCommitted(revision);
        } catch (StreamRevisionConflictException ex) {
            throw new ConcurrencyConflictException(
                    "CUSTOMER_CONCURRENT_MODIFICATION",
                    "O cadastro foi alterado por outra operação. Consulte novamente e repita a alteração.");
        }
    }
}
