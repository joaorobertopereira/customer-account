package br.com.helpcsistemas.customeraccount.customer.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerEmailLookupPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.privacy.BlindIndex;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** Consulta somente o índice cego do e-mail; o event store valida os candidatos antes de qualquer vínculo. */
@Component
class MongoCustomerEmailLookupAdapter implements CustomerEmailLookupPort {

    private static final String COLLECTION = "customer_views";
    private static final int CANDIDATE_LIMIT = 3;

    private final MongoTemplate mongo;
    private final BlindIndex blindIndex;

    MongoCustomerEmailLookupAdapter(MongoTemplate mongo, BlindIndex blindIndex) {
        this.mongo = mongo;
        this.blindIndex = blindIndex;
    }

    @Override
    public List<UUID> findCandidates(TenantId tenant, Email email) {
        var query = Query.query(Criteria.where("emailIndex")
                        .is(blindIndex.compute(MongoCustomerViewRepositoryAdapter.EMAIL_PURPOSE, email.value()))
                        .and("tenants")
                        .is(tenant.value())
                        .and("status")
                        .is("ACTIVE"))
                .limit(CANDIDATE_LIMIT);
        return mongo.find(query, Document.class, COLLECTION).stream()
                .map(document -> UUID.fromString(document.getString("_id")))
                .toList();
    }
}
