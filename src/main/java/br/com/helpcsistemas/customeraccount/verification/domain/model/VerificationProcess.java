package br.com.helpcsistemas.customeraccount.verification.domain.model;

import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.EventSourcedAggregate;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
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
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRequested;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationResultDelivered;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationSubmittedToProvider;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationValidityExpired;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma validação do titular. Falha técnica do primário aciona a contingência automaticamente (o fluxo não para);
 * a falha fica registrada. Só termina em {@link VerificationOutcome#FAILED} se nenhum provedor responder.
 *
 * <p>Validações de posse (OTP) passam por {@link VerificationStatus#AWAITING_CONFIRMATION}: o titular tem até
 * {@link #MAX_CHALLENGE_ATTEMPTS} tentativas antes do prazo expirar.
 */
public class VerificationProcess extends EventSourcedAggregate<VerificationEvent> {

    public static final Duration DEFAULT_CHALLENGE_TTL = Duration.ofMinutes(10);
    public static final Duration HOSTED_LINK_GRACE_PERIOD = Duration.ofMinutes(15);
    public static final int MAX_CHALLENGE_ATTEMPTS = 5;
    static final String SCORE_BELOW_THRESHOLD = "SCORE_BELOW_THRESHOLD";
    static final String ALL_PROVIDERS_UNAVAILABLE = "ALL_PROVIDERS_UNAVAILABLE";
    static final String TOO_MANY_ATTEMPTS = "TOO_MANY_ATTEMPTS";
    static final String CHALLENGE_EXPIRED = "CHALLENGE_EXPIRED";

    private VerificationId id;
    private TenantId tenantId;
    private UUID customerId;
    private VerificationRule rule;
    private long policyVersion;
    private String requestedBy;
    private VerificationStatus status;
    private ProviderId currentProvider;
    private int attempts;
    private boolean failedOver;
    private final List<ProviderFailure> failures = new ArrayList<>();
    private VerificationOutcome outcome;
    private Integer score;
    private List<String> reasonCodes = List.of();
    private String providerTransactionId;
    private Instant requestedAt;
    private Instant concludedAt;
    private Instant challengeExpiresAt;
    private String hostedSessionId;
    private String hostedLink;
    private Instant hostedLinkExpiresAt;
    private List<String> sharedFields = List.of();
    private Instant validUntil;
    private boolean validityExpired;
    private int challengeAttempts;
    private Instant deliveredAt;
    private boolean deliveryFailed;

    private VerificationProcess() {}

    public static VerificationProcess request(
            VerificationId id,
            TenantId tenantId,
            UUID customerId,
            VerificationRule rule,
            long policyVersion,
            String requestedBy,
            Clock clock) {
        Objects.requireNonNull(customerId, "customerId");
        if (!rule.enabled()) {
            throw new BusinessRuleException(
                    "VERIFICATION_DISABLED", "Validação " + rule.type() + " está desabilitada para o tenant");
        }
        var process = new VerificationProcess();
        process.raise(
                new VerificationRequested(id, tenantId, customerId, rule, policyVersion, requestedBy, clock.instant()));
        return process;
    }

    public static VerificationProcess rehydrate(List<VerificationEvent> history, long version) {
        if (history.isEmpty() || !(history.getFirst() instanceof VerificationRequested)) {
            throw new IllegalArgumentException("Histórico deve começar com VerificationRequested");
        }
        var process = new VerificationProcess();
        process.replay(history, version);
        return process;
    }

    public void submit(Clock clock) {
        ensurePending();
        raise(new VerificationSubmittedToProvider(id, currentProvider, attempts + 1, clock.instant()));
    }

    public void providerFailed(String errorCode, long latencyMs, Clock clock) {
        ensurePending();
        var failedProvider = currentProvider;
        raise(new ProviderCallFailed(id, failedProvider, errorCode, latencyMs, clock.instant()));
        if (!failedOver && rule.hasFallback()) {
            raise(new ProviderFailedOver(id, failedProvider, rule.fallback(), clock.instant()));
        } else {
            raise(new VerificationConcluded(
                    id,
                    VerificationOutcome.FAILED,
                    failedProvider,
                    null,
                    List.of(ALL_PROVIDERS_UNAVAILABLE),
                    null,
                    clock.instant()));
        }
    }

    public void responseReceived(ProviderResult result, Clock clock) {
        responseReceived(result, DEFAULT_CHALLENGE_TTL, clock);
    }

    public void responseReceived(ProviderResult result, Duration challengeTtl, Clock clock) {
        ensurePending();
        var now = clock.instant();
        raise(new ProviderResponseReceived(id, currentProvider, result, now));
        if (result.decision() == ProviderDecision.CHALLENGE_SENT) {
            raise(new ChallengeIssued(id, currentProvider, result.transactionId(), now.plus(challengeTtl), now));
            return;
        }
        conclude(result, new ArrayList<>(result.reasonCodes()), now);
    }

    /** Resultado da checagem da resposta do titular junto ao provedor que emitiu o desafio. */
    public void answerChallenge(ProviderResult check, Clock clock) {
        ensureAwaiting();
        if (expireIfDue(clock)) {
            return;
        }
        var now = clock.instant();
        raise(new ProviderResponseReceived(id, currentProvider, check, now));
        if (check.decision() == ProviderDecision.APPROVED) {
            conclude(check, new ArrayList<>(check.reasonCodes()), now);
            return;
        }
        raise(new ChallengeAnswerRejected(id, challengeAttempts + 1, now));
        if (challengeAttempts >= MAX_CHALLENGE_ATTEMPTS) {
            var reasons = new ArrayList<>(check.reasonCodes());
            reasons.add(TOO_MANY_ATTEMPTS);
            raise(new VerificationConcluded(
                    id, VerificationOutcome.REJECTED, currentProvider, null, reasons, providerTransactionId, now));
        }
    }

    /** Falha técnica ao checar a resposta: o desafio pertence a este provedor, então não há contingência. */
    public void challengeCheckFailed(String errorCode, long latencyMs, Clock clock) {
        ensureAwaiting();
        raise(new ProviderCallFailed(id, currentProvider, errorCode, latencyMs, clock.instant()));
    }

    public void issueHostedLink(String sessionId, String url, Instant expiresAt, List<String> fields, Clock clock) {
        ensurePending();
        if (rule.type() != VerificationType.IDENTITY) {
            throw new BusinessRuleException("HOSTED_IDENTITY_REQUIRED", "Link hospedado exige validação de identidade");
        }
        if (sessionId == null
                || sessionId.isBlank()
                || url == null
                || url.isBlank()
                || expiresAt == null
                || !expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Sessão hospedada inválida ou expirada");
        }
        raise(new VerificationLinkIssued(
                id, currentProvider, sessionId, url, expiresAt, fields == null ? List.of() : fields, clock.instant()));
    }

    public void receiveHostedResult(HostedResult result, Clock clock) {
        if (status != VerificationStatus.AWAITING_HOLDER) {
            ignoreLateHostedResult(result.providerEventId(), clock);
            return;
        }
        if (!hostedSessionId.equals(result.sessionId())) {
            throw new BusinessRuleException("HOSTED_SESSION_MISMATCH", "Resultado não corresponde à sessão ativa");
        }
        var now = clock.instant();
        raise(new ProviderResultReceived(id, currentProvider, result, now));
        var classification = HostedResultClassifier.classify(result, rule.hostedResultRules());
        raise(new VerificationConcluded(
                id,
                classification.outcome(),
                currentProvider,
                result.score(),
                result.reasonCodes(),
                result.sessionId(),
                classification.validUntil(),
                now));
    }

    public void ignoreLateHostedResult(String providerEventId, Clock clock) {
        raise(new LateProviderResultIgnored(
                id, currentProvider, providerEventId, "ALREADY_CONCLUDED", clock.instant()));
    }

    public boolean validityExpired(Clock clock) {
        if (status != VerificationStatus.COMPLETED
                || outcome != VerificationOutcome.APPROVED
                || validUntil == null
                || validityExpired
                || clock.instant().isBefore(validUntil)) {
            return false;
        }
        raise(new VerificationValidityExpired(id, validUntil, clock.instant()));
        return true;
    }

    /** @return true se a validação expirou agora */
    public boolean expireIfDue(Clock clock) {
        var now = clock.instant();
        boolean challengeExpired =
                status == VerificationStatus.AWAITING_CONFIRMATION && !now.isBefore(challengeExpiresAt);
        boolean hostedExpired = status == VerificationStatus.AWAITING_HOLDER
                && !now.isBefore(hostedLinkExpiresAt.plus(HOSTED_LINK_GRACE_PERIOD));
        if (!challengeExpired && !hostedExpired) {
            return false;
        }
        raise(new VerificationConcluded(
                id,
                VerificationOutcome.EXPIRED,
                currentProvider,
                null,
                List.of(challengeExpired ? CHALLENGE_EXPIRED : "HOSTED_LINK_EXPIRED"),
                providerTransactionId,
                now));
        return true;
    }

    public void markDelivered(int httpStatus, int deliveryAttempts, Clock clock) {
        if (status == VerificationStatus.COMPLETED && deliveredAt == null) {
            raise(new VerificationResultDelivered(id, httpStatus, deliveryAttempts, clock.instant()));
        }
    }

    public void markDeliveryFailed(int deliveryAttempts, Integer lastHttpStatus, Clock clock) {
        if (status == VerificationStatus.COMPLETED && deliveredAt == null && !deliveryFailed) {
            raise(new VerificationDeliveryFailed(id, deliveryAttempts, lastHttpStatus, clock.instant()));
        }
    }

    private void conclude(ProviderResult result, List<String> reasons, Instant now) {
        var verdict = decide(result, reasons);
        raise(new VerificationConcluded(
                id, verdict, currentProvider, result.score(), reasons, result.transactionId(), now));
    }

    private VerificationOutcome decide(ProviderResult result, List<String> reasons) {
        return switch (result.decision()) {
            case APPROVED -> {
                if (rule.minimumScore() != null && (result.score() == null || result.score() < rule.minimumScore())) {
                    reasons.add(SCORE_BELOW_THRESHOLD);
                    yield VerificationOutcome.REJECTED;
                }
                yield VerificationOutcome.APPROVED;
            }
            case REJECTED -> VerificationOutcome.REJECTED;
            case INCONCLUSIVE ->
                rule.onInconclusive() == InconclusiveAction.MANUAL_REVIEW
                        ? VerificationOutcome.MANUAL_REVIEW
                        : VerificationOutcome.REJECTED;
            case CHALLENGE_SENT -> throw new IllegalStateException("Desafio não é um veredito");
        };
    }

    @Override
    protected void apply(VerificationEvent event) {
        switch (event) {
            case VerificationRequested e -> {
                id = e.verificationId();
                tenantId = e.tenantId();
                customerId = e.customerId();
                rule = e.rule();
                policyVersion = e.policyVersion();
                requestedBy = e.requestedBy();
                status = VerificationStatus.PENDING;
                currentProvider = e.rule().primary();
                requestedAt = e.occurredAt();
            }
            case VerificationSubmittedToProvider e -> attempts = e.attempt();
            case ProviderCallFailed e ->
                failures.add(new ProviderFailure(e.provider(), e.errorCode(), e.latencyMs(), e.occurredAt()));
            case ProviderFailedOver e -> {
                failedOver = true;
                currentProvider = e.to();
            }
            case ProviderResponseReceived _ -> {}
            case ChallengeIssued e -> {
                status = VerificationStatus.AWAITING_CONFIRMATION;
                providerTransactionId = e.transactionId();
                challengeExpiresAt = e.expiresAt();
            }
            case ChallengeAnswerRejected e -> challengeAttempts = e.attempt();
            case VerificationLinkIssued e -> {
                status = VerificationStatus.AWAITING_HOLDER;
                hostedSessionId = e.sessionId();
                hostedLink = e.url();
                hostedLinkExpiresAt = e.expiresAt();
                sharedFields = e.sharedFields();
            }
            case ProviderResultReceived _ -> {}
            case LateProviderResultIgnored _ -> {}
            case VerificationValidityExpired _ -> validityExpired = true;
            case VerificationResultDelivered e -> deliveredAt = e.occurredAt();
            case VerificationDeliveryFailed _ -> deliveryFailed = true;
            case VerificationConcluded e -> {
                status = VerificationStatus.COMPLETED;
                outcome = e.outcome();
                currentProvider = e.provider();
                score = e.score();
                reasonCodes = e.reasonCodes();
                providerTransactionId = e.providerTransactionId();
                validUntil = e.validUntil();
                concludedAt = e.occurredAt();
            }
        }
    }

    private void ensurePending() {
        if (status != VerificationStatus.PENDING) {
            throw new IllegalStateException("Validação não está em processamento");
        }
    }

    private void ensureAwaiting() {
        if (status != VerificationStatus.AWAITING_CONFIRMATION) {
            throw new BusinessRuleException(
                    "VERIFICATION_NOT_AWAITING_CONFIRMATION", "Esta validação não aguarda confirmação");
        }
    }

    public boolean isPending() {
        return status == VerificationStatus.PENDING;
    }

    public Instant challengeExpiresAt() {
        return challengeExpiresAt;
    }

    public int challengeAttempts() {
        return challengeAttempts;
    }

    public String hostedSessionId() {
        return hostedSessionId;
    }

    public String hostedLink() {
        return hostedLink;
    }

    public Instant hostedLinkExpiresAt() {
        return hostedLinkExpiresAt;
    }

    public List<String> sharedFields() {
        return sharedFields;
    }

    public Instant validUntil() {
        return validUntil;
    }

    public boolean isValidityExpired() {
        return validityExpired;
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    public boolean deliveryFailed() {
        return deliveryFailed;
    }

    public VerificationId id() {
        return id;
    }

    public TenantId tenantId() {
        return tenantId;
    }

    public UUID customerId() {
        return customerId;
    }

    public VerificationRule rule() {
        return rule;
    }

    public long policyVersion() {
        return policyVersion;
    }

    public String requestedBy() {
        return requestedBy;
    }

    public VerificationStatus status() {
        return status;
    }

    public ProviderId currentProvider() {
        return currentProvider;
    }

    public boolean failedOver() {
        return failedOver;
    }

    public List<ProviderFailure> failures() {
        return List.copyOf(failures);
    }

    public VerificationOutcome outcome() {
        return outcome;
    }

    public Integer score() {
        return score;
    }

    public List<String> reasonCodes() {
        return reasonCodes;
    }

    public String providerTransactionId() {
        return providerTransactionId;
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public Instant concludedAt() {
        return concludedAt;
    }
}
