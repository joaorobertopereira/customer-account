package br.com.helpcsistemas.customeraccount.verification.application.port.in;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.query.ProviderIncidentView;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import java.util.List;
import java.util.UUID;

public interface FindVerificationUseCase {

    VerificationView findById(TenantId tenant, VerificationId id);

    List<VerificationView> findByCustomer(TenantId tenant, UUID customerId);

    List<ProviderIncidentView> recentIncidents(TenantId tenant, int limit);
}
