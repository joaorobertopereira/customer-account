package br.com.helpcsistemas.customeraccount.verification.application.query;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.TenantVerificationPolicy;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationRule;
import java.util.Comparator;
import java.util.List;

/** @param version versão vigente da política (gravada em cada validação solicitada) */
public record VerificationPolicyView(TenantId tenantId, long version, List<VerificationRule> rules) {

    public static VerificationPolicyView of(TenantVerificationPolicy policy) {
        return new VerificationPolicyView(
                policy.tenantId(),
                policy.version(),
                policy.rules().values().stream()
                        .sorted(Comparator.comparing(VerificationRule::type))
                        .toList());
    }
}
