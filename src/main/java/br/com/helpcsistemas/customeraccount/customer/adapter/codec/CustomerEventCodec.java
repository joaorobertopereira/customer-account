package br.com.helpcsistemas.customeraccount.customer.adapter.codec;

import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.AddressChangedV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.AddressV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.EmailChangedV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.ErasedV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.LinkedToTenantV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.MobilePhoneChangedV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.PersonalDataUpdatedV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.PersonalDataV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.RegisteredV1;
import br.com.helpcsistemas.customeraccount.customer.domain.event.AddressChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerErased;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerEvent;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkedToTenant;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerRegistered;
import br.com.helpcsistemas.customeraccount.customer.domain.event.EmailChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.MobilePhoneChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.PersonalDataUpdated;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.HashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduz eventos de domínio ⇄ eventos persistidos. Eventos com dados pessoais têm o payload inteiro cifrado com a
 * chave do titular; metadados carregam apenas identificadores pseudônimos.
 */
@Component
public class CustomerEventCodec {

    public static final String STREAM_PREFIX = "customer-";

    static final String REGISTERED = "CustomerRegistered.v1";
    static final String LINKED_TO_TENANT = "CustomerLinkedToTenant.v1";
    static final String PERSONAL_DATA_UPDATED = "PersonalDataUpdated.v1";
    static final String ADDRESS_CHANGED = "AddressChanged.v1";
    static final String EMAIL_CHANGED = "EmailChanged.v1";
    static final String MOBILE_PHONE_CHANGED = "MobilePhoneChanged.v1";
    static final String ERASED = "CustomerErased.v1";

    static final String META_CUSTOMER_ID = "customerId";
    static final String META_ENCRYPTED = "encrypted";

    private final JsonMapper json;
    private final PersonalDataCipher cipher;

    CustomerEventCodec(JsonMapper json, PersonalDataCipher cipher) {
        this.json = json;
        this.cipher = cipher;
    }

    public String streamOf(CustomerId id) {
        return STREAM_PREFIX + id.value();
    }

    public CustomerId customerIdOf(StoredEvent event) {
        return CustomerId.of(event.stream().substring(STREAM_PREFIX.length()));
    }

    public boolean isErasure(StoredEvent event) {
        return ERASED.equals(event.type());
    }

    public NewEvent encode(CustomerEvent event) {
        var subject = event.customerId().toString();
        var payload = json.writeValueAsBytes(toPayload(event));
        var metadata = new HashMap<String, String>();
        metadata.put(META_CUSTOMER_ID, subject);

        if (containsPersonalData(event)) {
            metadata.put(META_ENCRYPTED, "true");
            return NewEvent.binary(typeOf(event), cipher.encrypt(subject, payload), metadata);
        }
        metadata.put(META_ENCRYPTED, "false");
        return NewEvent.json(typeOf(event), payload, metadata);
    }

    /** @throws br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException se a chave foi destruída */
    public CustomerEvent decode(StoredEvent stored) {
        var data = "true".equals(stored.metadata().get(META_ENCRYPTED))
                ? cipher.decrypt(customerIdOf(stored).toString(), stored.data())
                : stored.data();

        return switch (stored.type()) {
            case REGISTERED -> {
                var p = json.readValue(data, RegisteredV1.class);
                yield new CustomerRegistered(
                        CustomerId.of(p.customerId()),
                        TenantId.of(p.registeredBy()),
                        new Cpf(p.cpf()),
                        p.personalData().toDomain(),
                        new Email(p.email()),
                        new MobilePhone(p.mobilePhone()),
                        AddressV1.toDomainOrNull(p.address()),
                        p.occurredAt());
            }
            case LINKED_TO_TENANT -> {
                var p = json.readValue(data, LinkedToTenantV1.class);
                yield new CustomerLinkedToTenant(
                        CustomerId.of(p.customerId()), TenantId.of(p.tenantId()), p.occurredAt());
            }
            case PERSONAL_DATA_UPDATED -> {
                var p = json.readValue(data, PersonalDataUpdatedV1.class);
                yield new PersonalDataUpdated(
                        CustomerId.of(p.customerId()),
                        TenantId.of(p.updatedBy()),
                        p.personalData().toDomain(),
                        p.occurredAt());
            }
            case ADDRESS_CHANGED -> {
                var p = json.readValue(data, AddressChangedV1.class);
                yield new AddressChanged(
                        CustomerId.of(p.customerId()),
                        TenantId.of(p.changedBy()),
                        p.address().toDomain(),
                        p.occurredAt());
            }
            case EMAIL_CHANGED -> {
                var p = json.readValue(data, EmailChangedV1.class);
                yield new EmailChanged(
                        CustomerId.of(p.customerId()),
                        TenantId.of(p.changedBy()),
                        new Email(p.email()),
                        p.occurredAt());
            }
            case MOBILE_PHONE_CHANGED -> {
                var p = json.readValue(data, MobilePhoneChangedV1.class);
                yield new MobilePhoneChanged(
                        CustomerId.of(p.customerId()),
                        TenantId.of(p.changedBy()),
                        new MobilePhone(p.mobilePhone()),
                        p.occurredAt());
            }
            case ERASED -> {
                var p = json.readValue(data, ErasedV1.class);
                yield new CustomerErased(CustomerId.of(p.customerId()), TenantId.of(p.requestedBy()), p.occurredAt());
            }
            default -> throw new IllegalStateException("Tipo de evento desconhecido: " + stored.type());
        };
    }

    private static boolean containsPersonalData(CustomerEvent event) {
        return !(event instanceof CustomerErased || event instanceof CustomerLinkedToTenant);
    }

    private static String typeOf(CustomerEvent event) {
        return switch (event) {
            case CustomerRegistered _ -> REGISTERED;
            case CustomerLinkedToTenant _ -> LINKED_TO_TENANT;
            case PersonalDataUpdated _ -> PERSONAL_DATA_UPDATED;
            case AddressChanged _ -> ADDRESS_CHANGED;
            case EmailChanged _ -> EMAIL_CHANGED;
            case MobilePhoneChanged _ -> MOBILE_PHONE_CHANGED;
            case CustomerErased _ -> ERASED;
        };
    }

    private static Object toPayload(CustomerEvent event) {
        return switch (event) {
            case CustomerRegistered e ->
                new RegisteredV1(
                        e.customerId().toString(),
                        e.registeredBy().value(),
                        e.cpf().value(),
                        PersonalDataV1.from(e.personalData()),
                        e.email().value(),
                        e.mobilePhone().value(),
                        AddressV1.from(e.address()),
                        e.occurredAt());
            case CustomerLinkedToTenant e ->
                new LinkedToTenantV1(e.customerId().toString(), e.tenantId().value(), e.occurredAt());
            case PersonalDataUpdated e ->
                new PersonalDataUpdatedV1(
                        e.customerId().toString(),
                        e.updatedBy().value(),
                        PersonalDataV1.from(e.personalData()),
                        e.occurredAt());
            case AddressChanged e ->
                new AddressChangedV1(
                        e.customerId().toString(), e.changedBy().value(), AddressV1.from(e.address()), e.occurredAt());
            case EmailChanged e ->
                new EmailChangedV1(
                        e.customerId().toString(),
                        e.changedBy().value(),
                        e.email().value(),
                        e.occurredAt());
            case MobilePhoneChanged e ->
                new MobilePhoneChangedV1(
                        e.customerId().toString(),
                        e.changedBy().value(),
                        e.mobilePhone().value(),
                        e.occurredAt());
            case CustomerErased e ->
                new ErasedV1(e.customerId().toString(), e.requestedBy().value(), e.occurredAt());
        };
    }
}
