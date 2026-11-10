package br.com.helpcsistemas.customeraccount.verification.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.eventsourcing.AggregateSnapshots;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationEventCodec;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProcessRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class EventStoreVerificationProcessRepository implements VerificationProcessRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(EventStoreVerificationProcessRepository.class);

    private final EventStore eventStore;
    private final VerificationEventCodec codec;
    private final AggregateSnapshots snapshots;
    private final PersonalDataCipher cipher;

    EventStoreVerificationProcessRepository(
            EventStore eventStore,
            VerificationEventCodec codec,
            AggregateSnapshots snapshots,
            PersonalDataCipher cipher) {
        this.eventStore = eventStore;
        this.codec = codec;
        this.snapshots = snapshots;
        this.cipher = cipher;
    }

    @Override
    public Optional<VerificationProcess> findById(VerificationId id) {
        try {
            return snapshots.load(
                    codec.processStreamOf(id),
                    VerificationProcess.class,
                    cipher::decrypt,
                    codec::decodeProcess,
                    stored -> VerificationProcess.rehydrate(
                            stored.stream().map(codec::decodeProcess).toList(),
                            stored.getLast().revision()));
        } catch (PersonalDataErasedException ex) {
            log.atDebug().addKeyValue("verification.id", id).log("Validação de titular eliminado");
            return Optional.empty();
        }
    }

    @Override
    public void save(VerificationProcess process) {
        if (!process.hasPendingEvents()) {
            return;
        }
        var stream = codec.processStreamOf(process.id());
        var previous = process.version();
        var events = process.pendingEvents().stream()
                .map(e -> codec.encodeProcess(e, process.customerId()))
                .toList();
        try {
            long revision = eventStore.append(stream, ExpectedRevision.of(previous), events);
            process.markCommitted(revision);
        } catch (StreamRevisionConflictException ex) {
            throw new ConcurrencyConflictException(
                    "VERIFICATION_CONCURRENT_MODIFICATION", "A validação foi alterada por outra operação");
        }
        var subject = process.customerId().toString();
        snapshots.maybeSnapshot(
                stream, process, previous, process.version(), subject, plain -> cipher.encrypt(subject, plain));
    }
}
