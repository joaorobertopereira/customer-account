package br.com.helpcsistemas.customeraccount.customer.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerEventCodec;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.eventsourcing.AggregateSnapshots;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Repositório event-sourced: carrega do último snapshot (a cada 10 eventos) + cauda, ou por replay do stream, e
 * grava apenas os eventos novos. O snapshot contém dados pessoais e é cifrado com a chave do titular.
 */
@Component
class EventStoreCustomerRepositoryAdapter implements CustomerRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(EventStoreCustomerRepositoryAdapter.class);

    private final EventStore eventStore;
    private final CustomerEventCodec codec;
    private final AggregateSnapshots snapshots;
    private final PersonalDataCipher cipher;

    EventStoreCustomerRepositoryAdapter(
            EventStore eventStore, CustomerEventCodec codec, AggregateSnapshots snapshots, PersonalDataCipher cipher) {
        this.eventStore = eventStore;
        this.codec = codec;
        this.snapshots = snapshots;
        this.cipher = cipher;
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        try {
            var customer = snapshots.load(
                    codec.streamOf(id),
                    Customer.class,
                    cipher::decrypt,
                    codec::decode,
                    stored -> Customer.rehydrate(
                            stored.stream().map(codec::decode).toList(),
                            stored.getLast().revision()));
            customer.ifPresent(c -> log.atDebug()
                    .addKeyValue("customer.id", id)
                    .addKeyValue("customer.version", c.version())
                    .log("Agregado reconstruído"));
            return customer;
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
        var stream = codec.streamOf(customer.id());
        var previous = customer.version();
        var events = customer.pendingEvents().stream().map(codec::encode).toList();
        try {
            long revision = eventStore.append(stream, ExpectedRevision.of(previous), events);
            customer.markCommitted(revision);
        } catch (StreamRevisionConflictException ex) {
            throw new ConcurrencyConflictException(
                    "CUSTOMER_CONCURRENT_MODIFICATION",
                    "O cadastro foi alterado por outra operação. Consulte novamente e repita a alteração.");
        }
        var subject = customer.id().toString();
        snapshots.maybeSnapshot(
                stream, customer, previous, customer.version(), subject, plain -> cipher.encrypt(subject, plain));
    }
}
