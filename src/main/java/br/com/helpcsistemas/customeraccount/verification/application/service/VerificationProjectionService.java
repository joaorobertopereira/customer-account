package br.com.helpcsistemas.customeraccount.verification.application.service;

import br.com.helpcsistemas.customeraccount.verification.application.port.in.ProjectVerificationEventUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderCallbackInboxPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderIncidentRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProcessRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.ProviderIncidentView;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.event.ProviderCallFailed;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationEvent;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationProcess;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Projeção: o read model é sempre o estado atual do processo (reconstruído do event store), o que torna o
 * reprocessamento naturalmente idempotente. Cada falha de provedor vira um incidente do tenant.
 */
@Service
class VerificationProjectionService implements ProjectVerificationEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerificationProjectionService.class);

    private final VerificationProcessRepositoryPort processes;
    private final VerificationViewRepositoryPort views;
    private final ProviderIncidentRepositoryPort incidents;
    private final HostedSessionRegistryPort hostedSessions;
    private final ProviderCallbackInboxPort callbackInbox;

    VerificationProjectionService(
            VerificationProcessRepositoryPort processes,
            VerificationViewRepositoryPort views,
            ProviderIncidentRepositoryPort incidents,
            HostedSessionRegistryPort hostedSessions,
            ProviderCallbackInboxPort callbackInbox) {
        this.processes = processes;
        this.views = views;
        this.incidents = incidents;
        this.hostedSessions = hostedSessions;
        this.callbackInbox = callbackInbox;
    }

    @Override
    public void project(VerificationEvent event, long revision) {
        processes.findById(event.verificationId()).ifPresent(process -> {
            views.save(VerificationView.of(process));
            if (event instanceof ProviderCallFailed failed) {
                incidents.save(incidentOf(process, failed, revision));
            }
        });
    }

    @Override
    public void eraseCustomerData(UUID customerId) {
        views.deleteByCustomer(customerId);
        hostedSessions.deleteByCustomer(customerId);
        callbackInbox.deleteByCustomer(customerId);
        log.atInfo().addKeyValue("customer.id", customerId).log("Resultados de validação do titular eliminados");
    }

    private static ProviderIncidentView incidentOf(
            VerificationProcess process, ProviderCallFailed failed, long revision) {
        var rule = process.rule();
        var continuedWith = failed.provider().equals(rule.primary()) && rule.hasFallback() ? rule.fallback() : null;
        return new ProviderIncidentView(
                process.id() + ":" + revision,
                process.tenantId(),
                process.id(),
                rule.type(),
                failed.provider(),
                failed.errorCode(),
                failed.latencyMs(),
                continuedWith,
                failed.occurredAt());
    }
}
