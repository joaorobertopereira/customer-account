package br.com.helpcsistemas.customeraccount.privacy.internal;

import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Key store em coleção dedicada. Em produção, use database/cluster separado do read model, com credencial própria,
 * e política de backup alinhada ao crypto-shredding (backups antigos também guardam DEKs embrulhadas).
 */
@Component
class MongoSubjectKeyStore implements SubjectKeyStore {

    static final String COLLECTION = "privacy_subject_keys";

    private final MongoTemplate mongo;

    MongoSubjectKeyStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Optional<SubjectKey> find(String subjectId) {
        var doc = mongo.findById(subjectId, Document.class, COLLECTION);
        return Optional.ofNullable(doc)
                .map(d -> new SubjectKey(
                        d.getString("_id"),
                        d.getString("wrappedKey"),
                        d.getString("provider"),
                        d.getDate("createdAt").toInstant()));
    }

    @Override
    public boolean insertIfAbsent(SubjectKey key) {
        try {
            mongo.insert(
                    new Document("_id", key.subjectId())
                            .append("wrappedKey", key.wrappedKey())
                            .append("provider", key.provider())
                            .append("createdAt", Date.from(key.createdAt())),
                    COLLECTION);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public void delete(String subjectId) {
        mongo.remove(Query.query(Criteria.where("_id").is(subjectId)), COLLECTION);
    }
}
