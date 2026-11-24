package br.com.helpcsistemas.customeraccount.consent.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentProjection;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentGranted;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class MongoConsentProjection implements ConsentProjection {
    private static final String COLLECTION = "consent_views";
    private final MongoTemplate mongo;
    private final PersonalDataCipher cipher;
    private final JsonMapper json;

    MongoConsentProjection(MongoTemplate mongo, PersonalDataCipher cipher, JsonMapper json) {
        this.mongo = mongo;
        this.cipher = cipher;
        this.json = json;
    }

    @Override
    public void save(
            UUID customer, TenantId tenant, long version, java.time.Instant linkedAt, List<ConsentGranted> granted) {
        var id = customer + ":" + tenant.value();
        // Upsert condicionado à versão: revisão antiga nunca sobrescreve uma mais nova (inclusive entre instâncias).
        var query = Query.query(Criteria.where("_id").is(id).and("version").lte(version));
        var update = new Update()
                .set("customerId", customer.toString())
                .set("tenantId", tenant.value())
                .set("version", version)
                .set("linkedAt", linkedAt.toString())
                .set("encryptedConsents", cipher.encrypt(customer.toString(), json.writeValueAsBytes(granted)));
        try {
            mongo.upsert(query, update, COLLECTION);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // já existe revisão mais nova
        }
    }

    @Override
    public void remove(UUID customer, TenantId tenant) {
        mongo.remove(Query.query(Criteria.where("_id").is(customer + ":" + tenant.value())), COLLECTION);
    }

    @Override
    public void removeStale(UUID customer, TenantId tenant, java.time.Instant currentGeneration) {
        var criteria = Criteria.where("_id").is(customer + ":" + tenant.value());
        if (currentGeneration != null) {
            criteria = criteria.and("linkedAt").ne(currentGeneration.toString());
        }
        mongo.remove(Query.query(criteria), COLLECTION);
    }

    @Override
    public void clear() {
        mongo.remove(new Query(), COLLECTION);
    }

    @Override
    public void erase(UUID customer) {
        mongo.remove(Query.query(Criteria.where("customerId").is(customer.toString())), COLLECTION);
    }

    record Document(
            @Id String id,
            String customerId,
            String tenantId,
            long version,
            String linkedAt,
            byte[] encryptedConsents) {}
}
