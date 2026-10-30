package br.com.helpcsistemas.customeraccount.verification.application.port.in;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderDescriptor;
import br.com.helpcsistemas.customeraccount.verification.application.query.VerificationPolicyView;
import br.com.helpcsistemas.customeraccount.verification.domain.event.VerificationRuleConfigured;
import java.util.List;

public interface QueryVerificationPolicyUseCase {

    VerificationPolicyView current(TenantId tenant);

    /** Todas as alterações, da mais antiga para a mais recente. */
    List<VerificationRuleConfigured> history(TenantId tenant);

    List<ProviderDescriptor> availableProviders();
}
