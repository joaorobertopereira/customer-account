package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkRequested;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerStatus;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CustomerLinkService {
    private static final Logger log = LoggerFactory.getLogger(CustomerLinkService.class);
    private final CustomerRepositoryPort customers;
    private final Clock clock;

    public CustomerLinkService(CustomerRepositoryPort customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    private Customer linked(TenantId tenant, UUID customer) {
        return customers
                .findById(new CustomerId(customer))
                .filter(c -> c.status() == CustomerStatus.ACTIVE && c.tenants().contains(tenant))
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
    }

    public List<CustomerLinkRequested> requests(TenantId tenant, UUID customer) {
        return linked(tenant, customer).linkRequests().values().stream()
                .filter(r -> r.expiresAt().isAfter(clock.instant()))
                .toList();
    }

    public List<CustomerLinkRequested> suggestions(TenantId tenant, UUID customer) {
        return List.copyOf(linked(tenant, customer).suggestions().values());
    }

    public void decide(TenantId tenant, UUID customer, UUID requestId, boolean approve) {
        var aggregate = linked(tenant, customer);
        aggregate.decideLink(tenant, requestId, approve, clock);
        customers.save(aggregate);
        audit(tenant, customer, approve ? "customer.link_approved" : "customer.link_rejected");
    }

    public void unlink(TenantId tenant, UUID customer, TenantId target) {
        var aggregate = linked(tenant, customer);
        aggregate.unlink(tenant, target, clock);
        customers.save(aggregate);
        audit(target, customer, "customer.unlinked");
    }

    public void expire(UUID customer, UUID requestId) {
        customers.findById(new CustomerId(customer)).ifPresent(c -> {
            c.expireLink(requestId, clock);
            customers.save(c);
        });
    }

    private static void audit(TenantId tenant, UUID customer, String action) {
        log.atInfo()
                .addKeyValue("audit.action", action)
                .addKeyValue("tenant.id", tenant)
                .addKeyValue("customer.id", customer)
                .log("Vínculo atualizado");
    }
}
