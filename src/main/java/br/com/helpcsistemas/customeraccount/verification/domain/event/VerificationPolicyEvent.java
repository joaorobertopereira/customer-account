package br.com.helpcsistemas.customeraccount.verification.domain.event;

import br.com.helpcsistemas.customeraccount.shared.domain.DomainEvent;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;

public sealed interface VerificationPolicyEvent extends DomainEvent permits VerificationRuleConfigured {

    TenantId tenantId();
}
