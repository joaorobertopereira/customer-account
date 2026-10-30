package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.customer.CustomerSnapshot;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerStatus;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CustomerDirectoryService implements CustomerDirectory {

    private final CustomerRepositoryPort repository;

    CustomerDirectoryService(CustomerRepositoryPort repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CustomerSnapshot> findLinkedCustomer(TenantId tenant, UUID customerId) {
        return repository
                .findById(new CustomerId(customerId))
                .filter(c -> c.status() == CustomerStatus.ACTIVE && c.tenants().contains(tenant))
                .map(c -> new CustomerSnapshot(
                        customerId,
                        c.cpf().value(),
                        c.personalData().fullName().value(),
                        c.personalData().birthDate(),
                        c.email().value(),
                        c.mobilePhone().value()));
    }
}
