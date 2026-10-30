package br.com.helpcsistemas.customeraccount.verification.application.service;

import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.FindVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderIncidentRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.application.query.ProviderIncidentView;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Consultas no read model, sempre restritas ao tenant do token. */
@Service
class VerificationQueryService implements FindVerificationUseCase {

    static final int MAX_INCIDENTS = 200;

    private final VerificationViewRepositoryPort views;
    private final ProviderIncidentRepositoryPort incidents;

    VerificationQueryService(VerificationViewRepositoryPort views, ProviderIncidentRepositoryPort incidents) {
        this.views = views;
        this.incidents = incidents;
    }

    @Override
    public VerificationView findById(TenantId tenant, VerificationId id) {
        return views.findById(id)
                .filter(v -> v.tenantId().equals(tenant))
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Validação não encontrada"));
    }

    @Override
    public List<VerificationView> findByCustomer(TenantId tenant, UUID customerId) {
        return views.findByTenantAndCustomer(tenant, customerId);
    }

    @Override
    public List<ProviderIncidentView> recentIncidents(TenantId tenant, int limit) {
        return incidents.findRecent(tenant, Math.clamp(limit, 1, MAX_INCIDENTS));
    }
}
