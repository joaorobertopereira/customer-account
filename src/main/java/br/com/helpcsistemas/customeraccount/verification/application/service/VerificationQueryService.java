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
    private final br.com.helpcsistemas.customeraccount.customer.CustomerDirectory customers;

    VerificationQueryService(
            VerificationViewRepositoryPort views,
            ProviderIncidentRepositoryPort incidents,
            br.com.helpcsistemas.customeraccount.customer.CustomerDirectory customers) {
        this.views = views;
        this.incidents = incidents;
        this.customers = customers;
    }

    @Override
    public VerificationView findById(TenantId tenant, VerificationId id) {
        return views.findById(id)
                .filter(v -> v.tenantId().equals(tenant))
                .filter(v -> customers
                        .linkedAt(tenant, v.customerId())
                        .filter(at -> !v.requestedAt().isBefore(at))
                        .isPresent())
                .orElseThrow(() -> new ResourceNotFoundException("VERIFICATION_NOT_FOUND", "Validação não encontrada"));
    }

    @Override
    public List<VerificationView> findByCustomer(TenantId tenant, UUID customerId) {
        var linkedAt = customers
                .linkedAt(tenant, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
        return views.findByTenantAndCustomer(tenant, customerId).stream()
                .filter(v -> !v.requestedAt().isBefore(linkedAt))
                .toList();
    }

    @Override
    public List<ProviderIncidentView> recentIncidents(TenantId tenant, int limit) {
        return incidents.findRecent(tenant, Math.clamp(limit, 1, MAX_INCIDENTS));
    }
}
