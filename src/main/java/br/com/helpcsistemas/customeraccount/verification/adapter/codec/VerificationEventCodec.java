package br.com.helpcsistemas.customeraccount.verification.adapter.codec;

import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.CallFailedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ChallengeIssuedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ChallengeRejectedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ConcludedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.DeliveredV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.DeliveryFailedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.FailedOverV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RequestedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ResponseReceivedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RuleConfiguredV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RuleV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.SubmittedV1;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ChallengeAnswerRejected;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ChallengeIssued;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderCallFailed;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderFailedOver;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderResponseReceived;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationConcluded;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationDeliveryFailed;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationPolicyEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRequested;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationResultDelivered;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationSubmittedToProvider;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialização dos eventos de política e de validação. Retornos de provedor e veredito são dados pessoais do
 * titular: payload cifrado com a chave dele (eliminados no crypto-shredding).
 */
@Component
public class VerificationEventCodec {

    public static final String PROCESS_STREAM_PREFIX = "verification-";
    public static final String POLICY_STREAM_PREFIX = "verification_policy-";

    static final String RULE_CONFIGURED = "VerificationRuleConfigured.v1";
    static final String REQUESTED = "VerificationRequested.v1";
    static final String SUBMITTED = "VerificationSubmittedToProvider.v1";
    static final String CALL_FAILED = "ProviderCallFailed.v1";
    static final String FAILED_OVER = "ProviderFailedOver.v1";
    static final String RESPONSE_RECEIVED = "ProviderResponseReceived.v1";
    static final String CONCLUDED = "VerificationConcluded.v1";
    static final String CHALLENGE_ISSUED = "ChallengeIssued.v1";
    static final String CHALLENGE_REJECTED = "ChallengeAnswerRejected.v1";
    static final String DELIVERED = "VerificationResultDelivered.v1";
    static final String DELIVERY_FAILED = "VerificationDeliveryFailed.v1";

    static final String META_CUSTOMER_ID = "customerId";
    static final String META_ENCRYPTED = "encrypted";

    private final JsonMapper json;
    private final PersonalDataCipher cipher;

    VerificationEventCodec(JsonMapper json, PersonalDataCipher cipher) {
        this.json = json;
        this.cipher = cipher;
    }

    public String policyStreamOf(TenantId tenant) {
        return POLICY_STREAM_PREFIX + tenant.value();
    }

    public String processStreamOf(VerificationId id) {
        return PROCESS_STREAM_PREFIX + id.value();
    }

    public NewEvent encodePolicy(VerificationPolicyEvent event) {
        return switch (event) {
            case VerificationRuleConfigured e ->
                NewEvent.json(
                        RULE_CONFIGURED,
                        json.writeValueAsBytes(new RuleConfiguredV1(
                                e.tenantId().value(),
                                RuleV1.from(e.rule()),
                                RuleV1.from(e.previous()),
                                e.changedBy(),
                                e.reason(),
                                e.occurredAt())),
                        Map.of());
        };
    }

    public VerificationPolicyEvent decodePolicy(StoredEvent stored) {
        if (!RULE_CONFIGURED.equals(stored.type())) {
            throw new IllegalStateException("Tipo de evento desconhecido: " + stored.type());
        }
        var p = json.readValue(stored.data(), RuleConfiguredV1.class);
        return new VerificationRuleConfigured(
                TenantId.of(p.tenantId()),
                RuleV1.toDomain(p.rule()),
                RuleV1.toDomain(p.previous()),
                p.changedBy(),
                p.reason(),
                p.occurredAt());
    }

    public NewEvent encodeProcess(VerificationEvent event, UUID customerId) {
        var id = event.verificationId().value();
        var payload = switch (event) {
            case VerificationRequested e ->
                new RequestedV1(
                        id,
                        e.tenantId().value(),
                        e.customerId(),
                        RuleV1.from(e.rule()),
                        e.policyVersion(),
                        e.requestedBy(),
                        e.occurredAt());
            case VerificationSubmittedToProvider e ->
                new SubmittedV1(id, e.provider().value(), e.attempt(), e.occurredAt());
            case ProviderCallFailed e ->
                new CallFailedV1(id, e.provider().value(), e.errorCode(), e.latencyMs(), e.occurredAt());
            case ProviderFailedOver e -> new FailedOverV1(id, e.from().value(), e.to().value(), e.occurredAt());
            case ProviderResponseReceived e ->
                new ResponseReceivedV1(
                        id,
                        e.provider().value(),
                        e.result().decision(),
                        e.result().score(),
                        e.result().reasonCodes(),
                        e.result().transactionId(),
                        e.result().latencyMs(),
                        e.result().rawResponse(),
                        e.occurredAt());
            case VerificationConcluded e ->
                new ConcludedV1(
                        id,
                        e.outcome(),
                        e.provider().value(),
                        e.score(),
                        e.reasonCodes(),
                        e.providerTransactionId(),
                        e.occurredAt());
            case ChallengeIssued e ->
                new ChallengeIssuedV1(id, e.provider().value(), e.transactionId(), e.expiresAt(), e.occurredAt());
            case ChallengeAnswerRejected e -> new ChallengeRejectedV1(id, e.attempt(), e.occurredAt());
            case VerificationResultDelivered e -> new DeliveredV1(id, e.httpStatus(), e.attempts(), e.occurredAt());
            case VerificationDeliveryFailed e ->
                new DeliveryFailedV1(id, e.attempts(), e.lastHttpStatus(), e.occurredAt());
        };
        var data = json.writeValueAsBytes(payload);
        var subject = customerId.toString();
        if (containsPersonalData(event)) {
            return NewEvent.binary(
                    typeOf(event),
                    cipher.encrypt(subject, data),
                    Map.of(META_CUSTOMER_ID, subject, META_ENCRYPTED, "true"));
        }
        return NewEvent.json(typeOf(event), data, Map.of(META_CUSTOMER_ID, subject, META_ENCRYPTED, "false"));
    }

    /** @throws br.com.helpcsistemas.customeraccount.privacy.PersonalDataErasedException se o titular foi eliminado */
    public VerificationEvent decodeProcess(StoredEvent stored) {
        var data = "true".equals(stored.metadata().get(META_ENCRYPTED))
                ? cipher.decrypt(stored.metadata().get(META_CUSTOMER_ID), stored.data())
                : stored.data();
        return switch (stored.type()) {
            case REQUESTED -> {
                var p = json.readValue(data, RequestedV1.class);
                yield new VerificationRequested(
                        new VerificationId(p.verificationId()),
                        p.tenant(),
                        p.customerId(),
                        RuleV1.toDomain(p.rule()),
                        p.policyVersion(),
                        p.requestedBy(),
                        p.occurredAt());
            }
            case SUBMITTED -> {
                var p = json.readValue(data, SubmittedV1.class);
                yield new VerificationSubmittedToProvider(
                        new VerificationId(p.verificationId()),
                        new ProviderId(p.provider()),
                        p.attempt(),
                        p.occurredAt());
            }
            case CALL_FAILED -> {
                var p = json.readValue(data, CallFailedV1.class);
                yield new ProviderCallFailed(
                        new VerificationId(p.verificationId()),
                        new ProviderId(p.provider()),
                        p.errorCode(),
                        p.latencyMs(),
                        p.occurredAt());
            }
            case FAILED_OVER -> {
                var p = json.readValue(data, FailedOverV1.class);
                yield new ProviderFailedOver(
                        new VerificationId(p.verificationId()),
                        new ProviderId(p.from()),
                        new ProviderId(p.to()),
                        p.occurredAt());
            }
            case RESPONSE_RECEIVED -> {
                var p = json.readValue(data, ResponseReceivedV1.class);
                yield new ProviderResponseReceived(
                        new VerificationId(p.verificationId()),
                        new ProviderId(p.provider()),
                        new ProviderResult(
                                p.decision(),
                                p.score(),
                                p.reasonCodes(),
                                p.transactionId(),
                                p.latencyMs(),
                                p.rawResponse()),
                        p.occurredAt());
            }
            case CONCLUDED -> {
                var p = json.readValue(data, ConcludedV1.class);
                yield new VerificationConcluded(
                        new VerificationId(p.verificationId()),
                        p.outcome(),
                        new ProviderId(p.provider()),
                        p.score(),
                        p.reasonCodes(),
                        p.providerTransactionId(),
                        p.occurredAt());
            }
            case CHALLENGE_ISSUED -> {
                var p = json.readValue(data, ChallengeIssuedV1.class);
                yield new ChallengeIssued(
                        new VerificationId(p.verificationId()),
                        new ProviderId(p.provider()),
                        p.transactionId(),
                        p.expiresAt(),
                        p.occurredAt());
            }
            case CHALLENGE_REJECTED -> {
                var p = json.readValue(data, ChallengeRejectedV1.class);
                yield new ChallengeAnswerRejected(new VerificationId(p.verificationId()), p.attempt(), p.occurredAt());
            }
            case DELIVERED -> {
                var p = json.readValue(data, DeliveredV1.class);
                yield new VerificationResultDelivered(
                        new VerificationId(p.verificationId()), p.httpStatus(), p.attempts(), p.occurredAt());
            }
            case DELIVERY_FAILED -> {
                var p = json.readValue(data, DeliveryFailedV1.class);
                yield new VerificationDeliveryFailed(
                        new VerificationId(p.verificationId()), p.attempts(), p.lastHttpStatus(), p.occurredAt());
            }
            default -> throw new IllegalStateException("Tipo de evento desconhecido: " + stored.type());
        };
    }

    private static boolean containsPersonalData(VerificationEvent event) {
        return event instanceof ProviderResponseReceived || event instanceof VerificationConcluded;
    }

    private static String typeOf(VerificationEvent event) {
        return switch (event) {
            case VerificationRequested _ -> REQUESTED;
            case VerificationSubmittedToProvider _ -> SUBMITTED;
            case ProviderCallFailed _ -> CALL_FAILED;
            case ProviderFailedOver _ -> FAILED_OVER;
            case ProviderResponseReceived _ -> RESPONSE_RECEIVED;
            case VerificationConcluded _ -> CONCLUDED;
            case ChallengeIssued _ -> CHALLENGE_ISSUED;
            case ChallengeAnswerRejected _ -> CHALLENGE_REJECTED;
            case VerificationResultDelivered _ -> DELIVERED;
            case VerificationDeliveryFailed _ -> DELIVERY_FAILED;
        };
    }

    public boolean isConclusion(StoredEvent event) {
        return CONCLUDED.equals(event.type());
    }

    public VerificationId verificationIdOf(StoredEvent event) {
        return VerificationId.of(event.stream().substring(PROCESS_STREAM_PREFIX.length()));
    }
}
