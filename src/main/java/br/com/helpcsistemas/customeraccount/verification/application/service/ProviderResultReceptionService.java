package br.com.helpcsistemas.customeraccount.verification.application.service;

import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ReceiveProviderResultUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProcessRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
class ProviderResultReceptionService implements ReceiveProviderResultUseCase {

    private final HostedSessionRegistryPort sessions;
    private final VerificationProcessRepositoryPort processes;
    private final Clock clock;

    ProviderResultReceptionService(
            HostedSessionRegistryPort sessions, VerificationProcessRepositoryPort processes, Clock clock) {
        this.sessions = sessions;
        this.processes = processes;
        this.clock = clock;
    }

    @Override
    @Observed(name = "verification.provider.callback", contextualName = "receive-hosted-verification-result")
    public void receive(ProviderId provider, HostedResult result) {
        var session = sessions.findByProviderSession(provider, result.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PROVIDER_SESSION_NOT_FOUND", "Sessão do provedor não encontrada"));
        var process = processes
                .findById(session.verificationId())
                .filter(p -> p.tenantId().equals(session.tenant()))
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Validação não encontrada"));
        if (!result.sessionId().equals(process.hostedSessionId())) {
            throw new ResourceNotFoundException("PROVIDER_SESSION_NOT_FOUND", "Sessão do provedor não encontrada");
        }
        if (process.expireIfDue(clock)) {
            process.ignoreLateHostedResult(result.providerEventId(), clock);
        } else if (process.status() == VerificationStatus.COMPLETED) {
            process.ignoreLateHostedResult(result.providerEventId(), clock);
        } else {
            process.receiveHostedResult(result, clock);
        }
        processes.save(process);
        if (process.status() == VerificationStatus.COMPLETED) {
            sessions.release(process.id());
        }
    }
}
