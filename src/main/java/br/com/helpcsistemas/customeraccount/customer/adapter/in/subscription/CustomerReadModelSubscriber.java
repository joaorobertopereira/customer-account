package br.com.helpcsistemas.customeraccount.customer.adapter.in.subscription;

import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerEventCodec;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.ProjectCustomerEventUseCase;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Serviço de projeção: consome os eventos de {@code customer-*} e mantém o read model no MongoDB. Para reconstruir o
 * read model, use {@code POST /api/v1/admin/subscriptions/customer-read-model-v1/rebuild} (role {@code admin}) numa
 * instância com subscriptions habilitadas: o endpoint interrompe a subscription, descarta o read model e o checkpoint
 * e reprocessa todo o histórico. Reconstrua esta projeção antes das de consentimento e validação, que consultam o
 * vínculo nela.
 */
@Component
class CustomerReadModelSubscriber implements EventStreamSubscriber {

    private static final Logger log = LoggerFactory.getLogger(CustomerReadModelSubscriber.class);

    private final CustomerEventCodec codec;
    private final ProjectCustomerEventUseCase projection;

    CustomerReadModelSubscriber(CustomerEventCodec codec, ProjectCustomerEventUseCase projection) {
        this.codec = codec;
        this.projection = projection;
    }

    @Override
    public String subscriptionName() {
        return "customer-read-model-v1";
    }

    @Override
    public boolean rebuildable() {
        return true;
    }

    @Override
    public void resetProjection() {
        projection.reset();
    }

    @Override
    public String streamPrefix() {
        return CustomerEventCodec.STREAM_PREFIX;
    }

    @Override
    public void onEvent(StoredEvent event) {
        try {
            projection.project(codec.decode(event), event.revision());
        } catch (PersonalDataErasedException ex) {
            // Replay de histórico de titular eliminado: os eventos cifrados não podem (e não devem) ser projetados.
            log.atDebug()
                    .addKeyValue("event.type", event.type())
                    .log("Evento de titular eliminado ignorado pela projeção");
        }
    }
}
