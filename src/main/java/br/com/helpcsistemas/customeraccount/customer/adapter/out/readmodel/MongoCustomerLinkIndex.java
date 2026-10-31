package br.com.helpcsistemas.customeraccount.customer.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerLinkIndex;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

@Component
class MongoCustomerLinkIndex implements CustomerLinkIndex {
    private static final String COLLECTION = "customer_link_requests";
    private final MongoTemplate mongo;

    MongoCustomerLinkIndex(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public void pending(UUID customer, UUID request, Instant at) {
        mongo.save(new Document(request.toString(), customer.toString(), at), COLLECTION);
    }

    @Override
    public void remove(UUID request) {
        mongo.remove(Query.query(Criteria.where("_id").is(request.toString())), COLLECTION);
    }

    @Override
    public List<ExpiredRequest> expired(Instant now) {
        return mongo
                .find(Query.query(Criteria.where("expiresAt").lte(now)).limit(100), Document.class, COLLECTION)
                .stream()
                .map(d -> new ExpiredRequest(UUID.fromString(d.customerId()), UUID.fromString(d.id())))
                .toList();
    }

    record Document(@Id String id, String customerId, Instant expiresAt) {}
}
