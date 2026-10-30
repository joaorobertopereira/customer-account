package br.com.helpcsistemas.customeraccount.customer.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.AddressV1;
import br.com.helpcsistemas.customeraccount.customer.adapter.codec.CustomerPayloads.PersonalDataV1;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.application.query.CustomerView;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerStatus;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.privacy.BlindIndex;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class MongoCustomerViewRepositoryAdapter implements CustomerViewRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoCustomerViewRepositoryAdapter.class);

    static final String COLLECTION = "customer_views";
    static final String CPF_PURPOSE = "cpf";
    static final String EMAIL_PURPOSE = "email";
    static final String MOBILE_PURPOSE = "mobile-phone";

    private final MongoTemplate mongo;
    private final PersonalDataCipher cipher;
    private final BlindIndex blindIndex;
    private final JsonMapper json;

    MongoCustomerViewRepositoryAdapter(
            MongoTemplate mongo, PersonalDataCipher cipher, BlindIndex blindIndex, JsonMapper json) {
        this.mongo = mongo;
        this.cipher = cipher;
        this.blindIndex = blindIndex;
        this.json = json;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            var indexes = mongo.indexOps(COLLECTION);
            indexes.createIndex(new Index().on("cpfIndex", Sort.Direction.ASC).named("ix_cpf"));
            indexes.createIndex(new Index().on("emailIndex", Sort.Direction.ASC).named("ix_email"));
            indexes.createIndex(
                    new Index().on("mobilePhoneIndex", Sort.Direction.ASC).named("ix_mobile_phone"));
            indexes.createIndex(new Index().on("tenants", Sort.Direction.ASC).named("ix_tenants"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar índices do read model");
        }
    }

    @Override
    public Optional<CustomerView> findById(CustomerId id) {
        return Optional.ofNullable(mongo.findById(id.toString(), CustomerDocument.class, COLLECTION))
                .flatMap(this::toView);
    }

    @Override
    public Optional<CustomerView> findByCpf(Cpf cpf) {
        var query = Query.query(Criteria.where("cpfIndex").is(blindIndex.compute(CPF_PURPOSE, cpf.value())));
        return Optional.ofNullable(mongo.findOne(query, CustomerDocument.class, COLLECTION))
                .flatMap(this::toView);
    }

    @Override
    public void save(CustomerView view) {
        mongo.save(toDocument(view), COLLECTION);
        log.atDebug()
                .addKeyValue("customer.id", view.id())
                .addKeyValue("customer.version", view.version())
                .log("Read model atualizado");
    }

    @Override
    public void deleteById(CustomerId id) {
        mongo.remove(Query.query(Criteria.where("_id").is(id.toString())), COLLECTION);
    }

    private CustomerDocument toDocument(CustomerView view) {
        var subject = view.id().toString();
        var profile = new ProfileV1(
                view.cpf().value(),
                PersonalDataV1.from(view.personalData()),
                view.email().value(),
                view.mobilePhone().value(),
                AddressV1.from(view.address()));
        return new CustomerDocument(
                subject,
                view.version(),
                view.status().name(),
                blindIndex.compute(CPF_PURPOSE, view.cpf().value()),
                blindIndex.compute(EMAIL_PURPOSE, view.email().value()),
                blindIndex.compute(MOBILE_PURPOSE, view.mobilePhone().value()),
                view.tenants().stream().map(TenantId::value).sorted().toList(),
                view.emailVerified(),
                view.mobilePhoneVerified(),
                view.createdAt(),
                view.updatedAt(),
                cipher.encrypt(subject, json.writeValueAsBytes(profile)));
    }

    private Optional<CustomerView> toView(CustomerDocument doc) {
        try {
            var profile = json.readValue(cipher.decrypt(doc.id(), doc.encryptedProfile()), ProfileV1.class);
            return Optional.of(new CustomerView(
                    CustomerId.of(doc.id()),
                    doc.version(),
                    CustomerStatus.valueOf(doc.status()),
                    new Cpf(profile.cpf()),
                    profile.personalData().toDomain(),
                    new Email(profile.email()),
                    doc.emailVerified(),
                    new MobilePhone(profile.mobilePhone()),
                    doc.mobilePhoneVerified(),
                    profile.address() == null ? null : profile.address().toDomain(),
                    doc.tenants().stream().map(TenantId::of).collect(Collectors.toSet()),
                    doc.createdAt(),
                    doc.updatedAt()));
        } catch (PersonalDataErasedException ex) {
            log.atDebug().addKeyValue("customer.id", doc.id()).log("Documento de titular eliminado ignorado");
            return Optional.empty();
        }
    }

    record ProfileV1(String cpf, PersonalDataV1 personalData, String email, String mobilePhone, AddressV1 address) {}
}
