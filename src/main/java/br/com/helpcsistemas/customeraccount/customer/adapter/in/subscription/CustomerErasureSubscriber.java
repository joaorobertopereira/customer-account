package br.com.helpcsistemas.customeraccount.customer.adapter.in.subscription;

import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerEventCodec;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.CompleteCustomerErasureUseCase;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import org.springframework.stereotype.Component;

/** Process manager da eliminação: reage a {@code CustomerErased} liberando o CPF e destruindo a chave do titular. */
@Component
class CustomerErasureSubscriber implements EventStreamSubscriber {

    private final CustomerEventCodec codec;
    private final CompleteCustomerErasureUseCase erasure;

    CustomerErasureSubscriber(CustomerEventCodec codec, CompleteCustomerErasureUseCase erasure) {
        this.codec = codec;
        this.erasure = erasure;
    }

    @Override
    public String subscriptionName() {
        return "customer-erasure-v1";
    }

    @Override
    public String streamPrefix() {
        return CustomerEventCodec.STREAM_PREFIX;
    }

    @Override
    public void onEvent(StoredEvent event) {
        if (codec.isErasure(event)) {
            erasure.completeErasure(codec.customerIdOf(event));
        }
    }
}
