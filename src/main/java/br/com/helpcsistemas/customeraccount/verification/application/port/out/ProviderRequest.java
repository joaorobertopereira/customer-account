package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;

/** O tenant segue na requisição para o adapter resolver as credenciais daquele tenant no provedor. */
public record ProviderRequest(
        TenantId tenantId, VerificationId verificationId, VerificationType type, VerificationSubject subject) {}
