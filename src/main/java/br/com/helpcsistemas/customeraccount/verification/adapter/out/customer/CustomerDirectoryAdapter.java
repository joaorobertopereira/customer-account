package br.com.helpcsistemas.customeraccount.verification.adapter.out.customer;

import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationSubject;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationSubjectPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Integração com o módulo Customer apenas pela API pública dele. */
@Component
class CustomerDirectoryAdapter implements VerificationSubjectPort {

    private final CustomerDirectory directory;

    CustomerDirectoryAdapter(CustomerDirectory directory) {
        this.directory = directory;
    }

    @Override
    public Optional<VerificationSubject> find(TenantId tenant, UUID customerId) {
        return directory
                .findLinkedCustomer(tenant, customerId)
                .map(c -> new VerificationSubject(
                        c.id(), c.cpf(), c.fullName(), c.birthDate(), c.email(), c.mobilePhone()));
    }
}
