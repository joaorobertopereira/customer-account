package br.com.helpcsistemas.customeraccount.verification.adapter.codec;

import br.com.helpcsistemas.customeraccount.eventsourcing.NewEvent;
import br.com.helpcsistemas.customeraccount.eventsourcing.StoredEvent;
import br.com.helpcsistemas.customeraccount.privacy.PersonalDataCipher;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.CallFailedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ChallengeIssuedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ChallengeRejectedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ConcludedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ConcludedV2;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.DeliveredV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.DeliveryFailedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.FailedOverV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.HostedResultV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.LateResultIgnoredV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.LinkIssuedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RequestedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RequestedV2;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ResponseReceivedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RuleConfiguredV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RuleConfiguredV2;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RuleV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.RuleV2;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.SubmittedV1;
import br.com.helpcsistemas.customeraccount.verification.adapter.codec.VerificationPayloads.ValidityExpiredV1;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ChallengeAnswerRejected;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ChallengeIssued;
import br.com.helpcsistemas.customeraccount.verification.domain.event.LateProviderResultIgnored;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderCallFailed;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderFailedOver;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderResponseReceived;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderResultReceived;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationConcluded;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationDeliveryFailed;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationLinkIssued;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationPolicyEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRequested;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationResultDelivered;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationSubmittedToProvider;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationValidityExpired;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
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

    static final String RULE_CONFIGURED_V1 = "VerificationRuleConfigured.v1";
    static final String RULE_CONFIGURED = "VerificationRuleConfigured.v2";
    static final String REQUESTED_V1 = "VerificationRequested.v1";
    static final String REQUESTED = "VerificationRequested.v2";
    static final String SUBMITTED = "VerificationSubmittedToProvider.v1";
    static final String CALL_FAILED = "ProviderCallFailed.v1";
    static final String FAILED_OVER = "ProviderFailedOver.v1";
    static final String RESPONSE_RECEIVED = "ProviderResponseReceived.v1";
    static final String CONCLUDED_V1 = "VerificationConcluded.v1";
    static final String CONCLUDED = "VerificationConcluded.v2";
    static final String CHALLENGE_ISSUED = "ChallengeIssued.v1";
    static final String CHALLENGE_REJECTED = "ChallengeAnswerRejected.v1";
    static final String DELIVERED = "VerificationResultDelivered.v1";
    static final String DELIVERY_FAILED = "VerificationDeliveryFailed.v1";
    static final String LINK_ISSUED = "VerificationLinkIssued.v1";
    static final String HOSTED_RESULT_RECEIVED = "ProviderResultReceived.v1";
    static final String LATE_RESULT_IGNORED = "LateProviderResultIgnored.v1";
    static final String VALIDITY_EXPIRED = "VerificationValidityExpired.v1";

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
                        json.writeValueAsBytes(new RuleConfiguredV2(
                                e.tenantId().value(),
                                RuleV2.from(e.rule()),
                                RuleV2.from(e.previous()),
                                e.changedBy(),
                                e.reason(),
                                e.occurredAt())),
                        Map.of());
        };
    }

    public VerificationPolicyEvent decodePolicy(StoredEvent stored) {
        if (RULE_CONFIGURED_V1.equals(stored.type())) {
            var p = json.readValue(stored.data(), RuleConfiguredV1.class);
            return new VerificationRuleConfigured(
                    TenantId.of(p.tenantId()),
                    RuleV1.toDomain(p.rule()),
                    RuleV1.toDomain(p.previous()),
                    p.changedBy(),
                    p.reason(),
                    p.occurredAt());
        }
        if (RULE_CONFIGURED.equals(stored.type())) {
            var p = json.readValue(stored.data(), RuleConfiguredV2.class);
            return new VerificationRuleConfigured(
                    TenantId.of(p.tenantId()),
                    RuleV2.toDomain(p.rule()),
                    RuleV2.toDomain(p.previous()),
                    p.changedBy(),
                    p.reason(),
                    p.occurredAt());
        }
        throw new IllegalStateException("Tipo de evento desconhecido: " + stored.type());
    }

    public NewEvent encodeProcess(VerificationEvent event, UUID customerId) {
        var id = event.verificationId().value();
        var payload = switch (event) {
            case VerificationRequested e ->
                new RequestedV2(
                        id,
                        e.tenantId().value(),
                        e.customerId(),
                        RuleV2.from(e.rule()),
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
                new ConcludedV2(
                        id,
                        e.outcome(),
                        e.provider().value(),
                        e.score(),
                        e.reasonCodes(),
                        e.providerTransactionId(),
                        e.validUntil(),
                        e.occurredAt());
            case ChallengeIssued e ->
                new ChallengeIssuedV1(id, e.provider().value(), e.transactionId(), e.expiresAt(), e.occurredAt());
            case ChallengeAnswerRejected e -> new ChallengeRejectedV1(id, e.attempt(), e.occurredAt());
            case VerificationLinkIssued e ->
                new LinkIssuedV1(
                        id,
                        e.provider().value(),
                        e.sessionId(),
                        e.url(),
                        e.expiresAt(),
                        e.sharedFields(),
                        e.occurredAt());
            case ProviderResultReceived e ->
                new HostedResultV1(
                        id,
                        e.provider().value(),
                        e.result().sessionId(),
                        e.result().status(),
                        e.result().score(),
                        e.result().reasonCodes(),
                        e.result().fraudSignals(),
                        e.result().providerEventId(),
                        e.result().occurredAt(),
                        e.result().rawResponse(),
                        e.occurredAt());
            case LateProviderResultIgnored e ->
                new LateResultIgnoredV1(id, e.provider().value(), e.providerEventId(), e.reason(), e.occurredAt());
            case VerificationValidityExpired e -> new ValidityExpiredV1(id, e.validUntil(), e.occurredAt());
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
            case REQUESTED_V1 -> {
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
            case REQUESTED -> {
                var p = json.readValue(data, RequestedV2.class);
                yield new VerificationRequested(
                        new VerificationId(p.verificationId()),
                        p.tenant(),
                        p.customerId(),
                        RuleV2.toDomain(p.rule()),
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
            case CONCLUDED_V1 -> {
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
            case CONCLUDED -> {
                var p = json.readValue(data, ConcludedV2.class);
                yield new VerificationConcluded(
                        new VerificationId(p.verificationId()),
                        p.outcome(),
                        new ProviderId(p.provider()),
                        p.score(),
                        p.reasonCodes(),
                        p.providerTransactionId(),
                        p.validUntil(),
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
            case LINK_ISSUED -> {
                var p = json.readValue(data, LinkIssuedV1.class);
                yield new VerificationLinkIssued(
                        new VerificationId(p.verificationId()),
                        new ProviderId(p.provider()),
                        p.sessionId(),
                        p.url(),
                        p.expiresAt(),
                        p.sharedFields(),
                        p.occurredAt());
            }
            case HOSTED_RESULT_RECEIVED -> {
                var p = json.readValue(data, HostedResultV1.class);
                yield new ProviderResultReceived(
                        new VerificationId(p.verificationId()),
                        new ProviderId(p.provider()),
                        new HostedResult(
                                p.sessionId(),
                                p.status(),
                                p.score(),
                                p.reasonCodes(),
                                p.fraudSignals(),
                                p.providerEventId(),
                                p.resultOccurredAt(),
                                p.rawResponse()),
                        p.occurredAt());
            }
            case LATE_RESULT_IGNORED -> {
                var p = json.readValue(data, LateResultIgnoredV1.class);
                yield new LateProviderResultIgnored(
                        new VerificationId(p.verificationId()),
                        new ProviderId(p.provider()),
                        p.providerEventId(),
                        p.reason(),
                        p.occurredAt());
            }
            case VALIDITY_EXPIRED -> {
                var p = json.readValue(data, ValidityExpiredV1.class);
                yield new VerificationValidityExpired(
                        new VerificationId(p.verificationId()), p.validUntil(), p.occurredAt());
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
        return event instanceof ProviderResponseReceived
                || event instanceof VerificationConcluded
                || event instanceof VerificationLinkIssued
                || event instanceof ProviderResultReceived;
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
            case VerificationLinkIssued _ -> LINK_ISSUED;
            case ProviderResultReceived _ -> HOSTED_RESULT_RECEIVED;
            case LateProviderResultIgnored _ -> LATE_RESULT_IGNORED;
            case VerificationValidityExpired _ -> VALIDITY_EXPIRED;
            case VerificationResultDelivered _ -> DELIVERED;
            case VerificationDeliveryFailed _ -> DELIVERY_FAILED;
        };
    }

    public boolean isConclusion(StoredEvent event) {
        return CONCLUDED.equals(event.type()) || CONCLUDED_V1.equals(event.type());
    }

    public boolean isValidityExpired(StoredEvent event) {
        return VALIDITY_EXPIRED.equals(event.type());
    }

    public VerificationId verificationIdOf(StoredEvent event) {
        return VerificationId.of(event.stream().substring(PROCESS_STREAM_PREFIX.length()));
    }
}
