package br.com.helpcsistemas.customeraccount.verification.adapter.out.eventstore;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import br.com.helpcsistemas.customeraccount.eventsourcing.ExpectedRevision;
import br.com.helpcsistemas.customeraccount.eventsourcing.StreamRevisionConflictException;
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

    EventStoreVerificationProcessRepository(EventStore eventStore, VerificationEventCodec codec) {
        this.eventStore = eventStore;
        this.codec = codec;
    }

    @Override
    public Optional<VerificationProcess> findById(VerificationId id) {
        var stored = eventStore.readStream(codec.processStreamOf(id));
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        try {
            var history = stored.stream().map(codec::decodeProcess).toList();
            return Optional.of(
                    VerificationProcess.rehydrate(history, stored.getLast().revision()));
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
        var events = process.pendingEvents().stream()
                .map(e -> codec.encodeProcess(e, process.customerId()))
                .toList();
        try {
            long revision = eventStore.append(
                    codec.processStreamOf(process.id()), ExpectedRevision.of(process.version()), events);
            process.markCommitted(revision);
        } catch (StreamRevisionConflictException ex) {
            throw new ConcurrencyConflictException(
                    "VERIFICATION_CONCURRENT_MODIFICATION", "A validação foi alterada por outra operação");
        }
    }
}
