package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.query.ProviderIncidentView;
import java.util.List;

public interface ProviderIncidentRepositoryPort {

    /** Upsert pelo id do incidente (idempotente). */
    void save(ProviderIncidentView incident);

    List<ProviderIncidentView> findRecent(TenantId tenant, int limit);
}
