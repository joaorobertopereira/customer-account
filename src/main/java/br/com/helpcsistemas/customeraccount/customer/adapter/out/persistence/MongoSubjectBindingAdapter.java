package br.com.helpcsistemas.customeraccount.customer.adapter.out.persistence;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectBindingRepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/** Índice imutável que impede um subject de ser vinculado novamente a outro cadastro. */
@Component
class MongoSubjectBindingAdapter implements SubjectBindingRepositoryPort {

    static final String COLLECTION = "subject_customer_bindings";
    private static final Logger log = LoggerFactory.getLogger(MongoSubjectBindingAdapter.class);

    private final MongoTemplate mongo;

    MongoSubjectBindingAdapter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            mongo.indexOps(COLLECTION)
                    .createIndex(new Index()
                            .on("customerId", Sort.Direction.ASC)
                            .unique()
                            .named("uq_subject_binding_customer"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar o índice de vínculos de identidade");
        }
    }

    @Override
    public boolean bindIfUnboundOrSame(String subject, UUID customerId) {
        var existing = find(subject);
        if (existing.isPresent()) {
            return existing.get().equals(customerId);
        }
        try {
            mongo.insert(new SubjectBinding(subject, customerId.toString()), COLLECTION);
            return true;
        } catch (DuplicateKeyException ex) {
            return find(subject).filter(customerId::equals).isPresent();
        }
    }

    @Override
    public Optional<UUID> findCustomerId(String subject) {
        return find(subject).map(UUID::fromString);
    }

    private Optional<String> find(String subject) {
        return Optional.ofNullable(mongo.findById(subject, SubjectBinding.class, COLLECTION))
                .map(SubjectBinding::customerId);
    }

    record SubjectBinding(@Id String subject, String customerId) {}
}
