package br.com.helpcsistemas.customeraccount.consent.adapter.out.customer;

import br.com.helpcsistemas.customeraccount.consent.ConsentDirectory;
import br.com.helpcsistemas.customeraccount.consent.application.service.ConsentService;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ConsentDirectoryAdapter implements ConsentDirectory {
    private final ConsentService service;

    ConsentDirectoryAdapter(ConsentService service) {
        this.service = service;
    }

    @Override
    public Set<String> allowedFields(TenantId tenant, UUID customer) {
        return service.allowedFields(tenant, customer);
    }
}
