package br.com.helpcsistemas.customeraccount.consent.adapter.in.subscription;

import br.com.helpcsistemas.customeraccount.consent.adapter.codec.ConsentEventCodec;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentRevoked;
import br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotification;
import br.com.helpcsistemas.customeraccount.tenant.TenantNotifications;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Avisa o tenant de cada revogação de consentimento. Tem efeito externo, por isso fica separado da projeção
 * {@link ConsentSubscriber} e não é reconstruível: reconstruir o read model não pode reenviar webhooks antigos.
 */
@Component
class ConsentRevocationNotifier implements EventStreamSubscriber {

    static final String NAME = "consent-revocation-notifier-v1";
    private static final String REVOKED_TYPE = "ConsentRevoked.v1";
    private static final Logger log = LoggerFactory.getLogger(ConsentRevocationNotifier.class);

    private final ConsentEventCodec codec;
    private final TenantNotifications notifications;
    private final JsonMapper json;

    ConsentRevocationNotifier(ConsentEventCodec codec, TenantNotifications notifications, JsonMapper json) {
        this.codec = codec;
        this.notifications = notifications;
        this.json = json;
    }

    @Override
    public String subscriptionName() {
        return NAME;
    }

    @Override
    public String streamPrefix() {
        return "consent-";
    }

    /** Até esta versão o aviso saía da projeção: começa de onde ela está para não reenviar o histórico. */
    @Override
    public Optional<String> inheritsCheckpointFrom() {
        return Optional.of(ConsentSubscriber.NAME);
    }

    @Override
    public void onEvent(StoredEvent stored) {
        if (!REVOKED_TYPE.equals(stored.type())) {
            return;
        }
        try {
            if (codec.decode(stored) instanceof ConsentRevoked revoked) {
                notifications.enqueue(new TenantNotification(
                        revoked.tenantId(),
                        stored.stream() + ":" + stored.revision(),
                        "consent.revoked",
                        revoked.purpose(),
                        revoked.customerId().toString(),
                        json.writeValueAsString(Map.of(
                                "eventType",
                                "consent.revoked",
                                "customerId",
                                revoked.customerId().toString(),
                                "purpose",
                                revoked.purpose(),
                                "occurredAt",
                                revoked.occurredAt())),
                        revoked.occurredAt()));
            }
        } catch (PersonalDataErasedException ex) {
            log.atInfo().addKeyValue("event.type", stored.type()).log("Revogação de titular eliminado ignorada");
        }
    }
}
