package br.com.helpcsistemas.customeraccount.verification.adapter.in.subscription;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationEventCodec;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ProjectVerificationEventUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class VerificationReadModelSubscriber implements EventStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(VerificationReadModelSubscriber.class);

    private final VerificationEventCodec codec;
    private final ProjectVerificationEventUseCase projection;

    VerificationReadModelSubscriber(VerificationEventCodec codec, ProjectVerificationEventUseCase projection) {
        this.codec = codec;
        this.projection = projection;
    }

    @Override
    public String subscriptionName() {
        return "verification-read-model-v1";
    }

    @Override
    public String streamPrefix() {
        return VerificationEventCodec.PROCESS_STREAM_PREFIX;
    }

    @Override
    public void onEvent(StoredEvent event) {
        try {
            projection.project(codec.decodeProcess(event), event.revision());
        } catch (PersonalDataErasedException ex) {
            log.atDebug().addKeyValue("event.type", event.type()).log("Evento de titular eliminado ignorado");
        }
    }
}
