package br.com.helpcsistemas.customeraccount.consent.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.consent.application.port.out.PurposeRepository;
import br.com.helpcsistemas.customeraccount.consent.domain.model.Purpose;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
class MongoPurposeRepository implements PurposeRepository {
    private static final String COLLECTION = "tenant_purposes";
    private final MongoTemplate mongo;

    MongoPurposeRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void save(TenantId tenant, Purpose purpose) {
        mongo.save(
                new Document(
                        tenant.value() + ":" + purpose.code(),
                        tenant.value(),
                        purpose.code(),
                        purpose.description(),
                        purpose.legalBasis(),
                        purpose.fields(),
                        purpose.policyVersion()),
                COLLECTION);
    }

    @Override
    public Optional<Purpose> find(TenantId tenant, String code) {
        return Optional.ofNullable(mongo.findById(tenant.value() + ":" + code, Document.class, COLLECTION))
                .map(Document::purpose);
    }

    @Override
    public List<Purpose> list(TenantId tenant) {
        return mongo.find(Query.query(Criteria.where("tenant").is(tenant.value())), Document.class, COLLECTION).stream()
                .map(Document::purpose)
                .toList();
    }

    record Document(
            @Id String id,
            String tenant,
            String code,
            String description,
            Purpose.LegalBasis legalBasis,
            Set<String> fields,
            String policyVersion) {
        Purpose purpose() {
            return new Purpose(code, description, legalBasis, fields, policyVersion);
        }
    }
}
