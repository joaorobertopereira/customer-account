package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.customer.CustomerSnapshot;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerEmailLookupPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerStatus;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CustomerDirectoryService implements CustomerDirectory {

    private final CustomerRepositoryPort repository;
    private final CustomerEmailLookupPort emailLookup;

    CustomerDirectoryService(CustomerRepositoryPort repository, CustomerEmailLookupPort emailLookup) {
        this.repository = repository;
        this.emailLookup = emailLookup;
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

    @Override
    public Optional<CustomerSnapshot> findLinkedCustomerByEmail(TenantId tenant, String verifiedEmail) {
        var email = Email.of(verifiedEmail);
        var candidates = emailLookup.findCandidates(tenant, email).stream()
                .distinct()
                .map(id -> repository.findById(new CustomerId(id)))
                .flatMap(Optional::stream)
                .filter(c -> c.status() == CustomerStatus.ACTIVE)
                .filter(c -> c.tenants().contains(tenant))
                .filter(c -> c.email().equals(email))
                .toList();
        if (candidates.size() > 1) {
            throw new BusinessRuleException(
                    "CUSTOMER_LINK_AMBIGUOUS", "O e-mail corresponde a mais de um cadastro neste tenant");
        }
        return candidates.stream()
                .findFirst()
                .map(c -> new CustomerSnapshot(
                        c.id().value(),
                        c.cpf().value(),
                        c.personalData().fullName().value(),
                        c.personalData().birthDate(),
                        c.email().value(),
                        c.mobilePhone().value()));
    }

    @Override
    public Set<TenantId> findLinkedTenantMemberships(TenantId tenant, UUID customerId) {
        return repository
                .findById(new CustomerId(customerId))
                .filter(c -> c.status() == CustomerStatus.ACTIVE && c.tenants().contains(tenant))
                .map(c -> c.tenants())
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
    }
}
