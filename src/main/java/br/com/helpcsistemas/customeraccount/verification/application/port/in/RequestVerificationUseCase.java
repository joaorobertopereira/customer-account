package br.com.helpcsistemas.customeraccount.verification.application.port.in;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.net.URI;
import java.util.UUID;

public interface RequestVerificationUseCase {

    VerificationView request(TenantId tenant, UUID customerId, VerificationType type, String requestedBy);

    default VerificationView request(
            TenantId tenant, UUID customerId, VerificationType type, String requestedBy, URI returnUrl) {
        return request(tenant, customerId, type, requestedBy);
    }
}
