package br.com.helpcsistemas.customeraccount.verification.application.service;

import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ConfirmVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.RequestVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort.HostedSessionReference;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderUnavailableException;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationPolicyRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProcessRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProviderGateway;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationSubjectPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import io.micrometer.observation.annotation.Observed;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orquestra uma validação: lê a política vigente do tenant (a cada chamada), registra a solicitação antes de chamar o
 * provedor e, em falha técnica, segue automaticamente para a contingência. Também confirma desafios (OTP).
 */
@Service
class VerificationService implements RequestVerificationUseCase, ConfirmVerificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final VerificationPolicyRepositoryPort policies;
    private final VerificationProcessRepositoryPort processes;
    private final VerificationSubjectPort subjects;
    private final VerificationProviderGateway providers;
    private final Clock clock;
    private final HostedSessionRegistryPort hostedSessions;

    VerificationService(
            VerificationPolicyRepositoryPort policies,
            VerificationProcessRepositoryPort processes,
            VerificationSubjectPort subjects,
            VerificationProviderGateway providers,
            Clock clock) {
        this(policies, processes, subjects, providers, clock, null);
    }

    @Autowired
    VerificationService(
            VerificationPolicyRepositoryPort policies,
            VerificationProcessRepositoryPort processes,
            VerificationSubjectPort subjects,
            VerificationProviderGateway providers,
            Clock clock,
            HostedSessionRegistryPort hostedSessions) {
        this.policies = policies;
        this.processes = processes;
        this.subjects = subjects;
        this.providers = providers;
        this.clock = clock;
        this.hostedSessions = hostedSessions;
    }

    @Override
    @Observed(name = "verification.request", contextualName = "request-verification")
    public VerificationView request(TenantId tenant, UUID customerId, VerificationType type, String requestedBy) {
        return request(tenant, customerId, type, requestedBy, null);
    }

    @Override
    @Observed(name = "verification.request", contextualName = "request-hosted-verification")
    public VerificationView request(
            TenantId tenant, UUID customerId, VerificationType type, String requestedBy, URI returnUrl) {
        var policy = policies.load(tenant);
        var rule = policy.ruleFor(type)
                .orElseThrow(() -> new BusinessRuleException(
                        "VERIFICATION_NOT_CONFIGURED", "O tenant não configurou provedor para validação " + type));
        var subject = subjects.find(tenant, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));

        if (type == VerificationType.IDENTITY && hostedSessions != null) {
            var active = hostedSessions.findActive(tenant, customerId, type, clock.instant());
            if (active.isPresent()) {
                if (!active.get().ready()) {
                    throw new BusinessRuleException(
                            "VERIFICATION_REQUEST_IN_PROGRESS", "A criação do link desta validação está em andamento");
                }
                return processes
                        .findById(active.get().verificationId())
                        .filter(p -> p.tenantId().equals(tenant) && p.status() == VerificationStatus.AWAITING_HOLDER)
                        .map(VerificationView::of)
                        .map(VerificationView::asReused)
                        .orElseThrow(() ->
                                new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Validação não encontrada"));
            }
            validateReturnUrl(rule, returnUrl);
        } else if (returnUrl != null) {
            throw new BusinessRuleException(
                    "RETURN_URL_NOT_SUPPORTED", "returnUrl só é aceito para validação de identidade");
        }

        var process = VerificationProcess.request(
                VerificationId.newId(), tenant, customerId, rule, policy.version(), requestedBy, clock);
        boolean hosted = type == VerificationType.IDENTITY && hostedSessions != null;
        if (hosted
                && !hostedSessions.claim(
                        tenant,
                        customerId,
                        type,
                        process.id(),
                        clock.instant().plus(HostedSessionRegistryPort.LEASE),
                        clock.instant())) {
            var active = hostedSessions.findActive(tenant, customerId, type, clock.instant());
            if (active.isPresent() && active.get().ready()) {
                return processes
                        .findById(active.get().verificationId())
                        .filter(p -> p.tenantId().equals(tenant))
                        .map(VerificationView::of)
                        .map(VerificationView::asReused)
                        .orElseThrow(() ->
                                new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Validação não encontrada"));
            }
            throw new BusinessRuleException(
                    "VERIFICATION_REQUEST_IN_PROGRESS", "A criação do link desta validação está em andamento");
        }
        processes.save(process);

        try {
            while (process.isPending()) {
                process.submit(clock);
                var provider = process.currentProvider();
                try {
                    var providerRequest = new ProviderRequest(tenant, process.id(), type, subject);
                    if (hosted) {
                        var session = providers.startHosted(provider, providerRequest, returnUrl);
                        process.issueHostedLink(
                                session.sessionId(), session.url(), session.expiresAt(), session.sharedFields(), clock);
                        processes.save(process);
                        hostedSessions.activate(new HostedSessionReference(
                                tenant,
                                customerId,
                                type,
                                process.id(),
                                provider,
                                session.sessionId(),
                                session.expiresAt(),
                                true));
                        return VerificationView.of(process);
                    }
                    var result = providers.verify(provider, providerRequest);
                    process.responseReceived(result, clock);
                } catch (ProviderUnavailableException ex) {
                    log.atWarn()
                            .addKeyValue("verification.id", process.id())
                            .addKeyValue("tenant.id", tenant)
                            .addKeyValue("provider.id", provider)
                            .addKeyValue("provider.errorCode", ex.errorCode())
                            .log("Falha no provedor de validação; seguindo para a contingência, se houver");
                    process.providerFailed(ex.errorCode(), ex.latencyMs(), clock);
                }
            }
            processes.save(process);
            if (hosted) {
                hostedSessions.release(process.id());
            }
        } catch (RuntimeException ex) {
            if (hosted) {
                hostedSessions.release(process.id());
            }
            throw ex;
        }

        log.atInfo()
                .addKeyValue("verification.id", process.id())
                .addKeyValue("tenant.id", tenant)
                .addKeyValue("customer.id", customerId)
                .addKeyValue("verification.type", type)
                .addKeyValue("verification.outcome", process.outcome())
                .addKeyValue("provider.id", process.currentProvider())
                .addKeyValue("verification.failedOver", process.failedOver())
                .addKeyValue("policy.version", process.policyVersion())
                .log("Validação processada");
        return VerificationView.of(process);
    }

    private static void validateReturnUrl(VerificationRule rule, URI returnUrl) {
        if (returnUrl == null) {
            return;
        }
        if (!"https".equalsIgnoreCase(returnUrl.getScheme())
                || returnUrl.getHost() == null
                || returnUrl.getRawUserInfo() != null
                || returnUrl.getRawFragment() != null) {
            throw new BusinessRuleException(
                    "INVALID_RETURN_URL", "returnUrl deve ser HTTPS e não pode ter credenciais ou fragmento");
        }
        var allowed = rule.hostedResultRules().allowedReturnUrlPrefixes().stream()
                .anyMatch(prefix -> {
                    try {
                        var base = URI.create(prefix);
                        return "https".equalsIgnoreCase(base.getScheme())
                                && returnUrl.getHost().equalsIgnoreCase(base.getHost())
                                && java.util.Objects.equals(returnUrl.getPort(), base.getPort())
                                && pathWithinPrefix(returnUrl.getPath(), base.getPath());
                    } catch (IllegalArgumentException ex) {
                        return false;
                    }
                });
        if (!allowed) {
            throw new BusinessRuleException(
                    "RETURN_URL_NOT_ALLOWED", "returnUrl não está na lista permitida do tenant");
        }
    }

    private static boolean pathWithinPrefix(String path, String prefix) {
        var normalized = prefix.endsWith("/") ? prefix : prefix + "/";
        return path.equals(prefix) || path.startsWith(normalized);
    }

    @Override
    @Observed(name = "verification.confirm", contextualName = "confirm-verification")
    public VerificationView confirm(TenantId tenant, VerificationId id, String answer) {
        var process = processes
                .findById(id)
                .filter(p -> p.tenantId().equals(tenant))
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Validação não encontrada"));
        if (process.expireIfDue(clock)) {
            processes.save(process);
            return VerificationView.of(process);
        }
        if (process.status() != VerificationStatus.AWAITING_CONFIRMATION) {
            throw new BusinessRuleException(
                    "VERIFICATION_NOT_AWAITING_CONFIRMATION", "Esta validação não aguarda confirmação");
        }
        var subject = subjects.find(tenant, process.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
        var provider = process.currentProvider();
        try {
            var check = providers.confirm(
                    provider,
                    new ProviderRequest(tenant, id, process.rule().type(), subject),
                    process.providerTransactionId(),
                    answer);
            process.answerChallenge(check, clock);
        } catch (ProviderUnavailableException ex) {
            process.challengeCheckFailed(ex.errorCode(), ex.latencyMs(), clock);
            processes.save(process);
            throw new BusinessRuleException(
                    "PROVIDER_TEMPORARILY_UNAVAILABLE", "Não foi possível confirmar agora. Tente novamente.");
        }
        processes.save(process);
        log.atInfo()
                .addKeyValue("verification.id", id)
                .addKeyValue("tenant.id", tenant)
                .addKeyValue("verification.status", process.status())
                .addKeyValue("verification.outcome", process.outcome())
                .addKeyValue("verification.challengeAttempts", process.challengeAttempts())
                .log("Resposta ao desafio processada");
        return VerificationView.of(process);
    }
}
