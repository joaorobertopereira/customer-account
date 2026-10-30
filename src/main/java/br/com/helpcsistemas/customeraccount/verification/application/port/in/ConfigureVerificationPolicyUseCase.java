package br.com.helpcsistemas.customeraccount.verification.application.port.in;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationPolicyView;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;

public interface ConfigureVerificationPolicyUseCase {

    /** Vale a partir da próxima validação solicitada; validações em andamento não são afetadas. */
    VerificationPolicyView configure(TenantId tenant, VerificationRule rule, String changedBy, String reason);
}
