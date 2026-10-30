package br.com.helpcsistemas.customeraccount.verification.adapter.out.readmodel;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderIncidentRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.ProviderIncidentView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/** Registro, por tenant, das falhas de provedores (inclusive as contornadas pela contingência). */
@Component
class MongoProviderIncidentRepository implements ProviderIncidentRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MongoProviderIncidentRepository.class);
    static final String COLLECTION = "verification_provider_incidents";

    private final MongoTemplate mongo;

    MongoProviderIncidentRepository(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        try {
            mongo.indexOps(COLLECTION)
                    .createIndex(new Index()
                            .on("tenantId", Sort.Direction.ASC)
                            .on("occurredAt", Sort.Direction.DESC)
                            .named("ix_tenant_occurred"));
        } catch (RuntimeException ex) {
            log.atWarn().setCause(ex).log("Não foi possível criar índices de incidentes");
        }
    }

    @Override
    public void save(ProviderIncidentView incident) {
        mongo.save(IncidentDocument.from(incident), COLLECTION);
    }

    @Override
    public List<ProviderIncidentView> findRecent(TenantId tenant, int limit) {
        var query = Query.query(Criteria.where("tenantId").is(tenant.value()))
                .with(Sort.by(Sort.Direction.DESC, "occurredAt"))
                .limit(limit);
        return mongo.find(query, IncidentDocument.class, COLLECTION).stream()
                .map(IncidentDocument::toView)
                .toList();
    }

    record IncidentDocument(
            @Id String id,
            String tenantId,
            String verificationId,
            String type,
            String provider,
            String errorCode,
            long latencyMs,
            String continuedWith,
            Instant occurredAt) {

        static IncidentDocument from(ProviderIncidentView v) {
            return new IncidentDocument(
                    v.id(),
                    v.tenantId().value(),
                    v.verificationId().toString(),
                    v.type().name(),
                    v.provider().value(),
                    v.errorCode(),
                    v.latencyMs(),
                    v.continuedWith() == null ? null : v.continuedWith().value(),
                    v.occurredAt());
        }

        ProviderIncidentView toView() {
            return new ProviderIncidentView(
                    id,
                    TenantId.of(tenantId),
                    VerificationId.of(verificationId),
                    VerificationType.valueOf(type),
                    new ProviderId(provider),
                    errorCode,
                    latencyMs,
                    continuedWith == null ? null : new ProviderId(continuedWith),
                    occurredAt);
        }
    }
}
