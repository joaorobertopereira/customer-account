package br.com.helpcsistemas.customeraccount.verification.adapter.in.subscription;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationEventCodec;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.VerificationLifecycleUseCase;
import org.springframework.stereotype.Component;

/** Ao concluir uma validação, agenda a entrega do resultado ao webhook do tenant (outbox sobre o event store). */
@Component
class VerificationResultNotificationSubscriber implements EventStreamSubscriber {

    private final VerificationEventCodec codec;
    private final VerificationLifecycleUseCase lifecycle;

    VerificationResultNotificationSubscriber(VerificationEventCodec codec, VerificationLifecycleUseCase lifecycle) {
        this.codec = codec;
        this.lifecycle = lifecycle;
    }

    @Override
    public String subscriptionName() {
        return "verification-result-notifier-v1";
    }

    @Override
    public String streamPrefix() {
        return VerificationEventCodec.PROCESS_STREAM_PREFIX;
    }

    @Override
    public void onEvent(StoredEvent event) {
        if (codec.isConclusion(event)) {
            lifecycle.notifyResult(codec.verificationIdOf(event));
        } else if (codec.isValidityExpired(event)) {
            lifecycle.notifyValidityExpired(codec.verificationIdOf(event));
        }
    }
}
