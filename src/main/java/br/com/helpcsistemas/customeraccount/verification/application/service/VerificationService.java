package br.com.helpcsistemas.customeraccount.verification.application.service;

import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ConfirmVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.RequestVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderUnavailableException;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationPolicyRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProcessRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProviderGateway;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationSubjectPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    VerificationService(
            VerificationPolicyRepositoryPort policies,
            VerificationProcessRepositoryPort processes,
            VerificationSubjectPort subjects,
            VerificationProviderGateway providers,
            Clock clock) {
        this.policies = policies;
        this.processes = processes;
        this.subjects = subjects;
        this.providers = providers;
        this.clock = clock;
    }

    @Override
    @Observed(name = "verification.request", contextualName = "request-verification")
    public VerificationView request(TenantId tenant, UUID customerId, VerificationType type, String requestedBy) {
        var policy = policies.load(tenant);
        var rule = policy.ruleFor(type)
                .orElseThrow(() -> new BusinessRuleException(
                        "VERIFICATION_NOT_CONFIGURED", "O tenant não configurou provedor para validação " + type));
        var subject = subjects.find(tenant, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));

        var process = VerificationProcess.request(
                VerificationId.newId(), tenant, customerId, rule, policy.version(), requestedBy, clock);
        processes.save(process);

        while (process.isPending()) {
            process.submit(clock);
            var provider = process.currentProvider();
            try {
                var result = providers.verify(provider, new ProviderRequest(tenant, process.id(), type, subject));
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
