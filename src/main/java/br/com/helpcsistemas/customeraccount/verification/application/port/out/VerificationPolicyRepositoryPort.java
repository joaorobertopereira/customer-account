package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import br.com.helpcsistemas.customeraccount.verification.domain.model.TenantVerificationPolicy;
import java.util.List;

public interface VerificationPolicyRepositoryPort {

    /** Sempre lê o estado atual (sem cache): garante que a última alteração vale na próxima validação. */
    TenantVerificationPolicy load(TenantId tenant);

    void save(TenantVerificationPolicy policy);

    List<VerificationRuleConfigured> history(TenantId tenant);
}
