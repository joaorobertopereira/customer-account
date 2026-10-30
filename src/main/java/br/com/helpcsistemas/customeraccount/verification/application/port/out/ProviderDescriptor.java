package br.com.helpcsistemas.customeraccount.verification.application.port.out;

import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.util.Set;

public record ProviderDescriptor(ProviderId id, Set<VerificationType> supportedTypes) {

    public ProviderDescriptor {
        supportedTypes = Set.copyOf(supportedTypes);
    }
}
