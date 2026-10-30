package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.application.port.in.UpdateCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Address;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.customer.domain.model.PersonalData;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class CustomerMaintenanceService implements UpdateCustomerUseCase {

    private static final Logger log = LoggerFactory.getLogger(CustomerMaintenanceService.class);

    private final CustomerRepositoryPort repository;
    private final Clock clock;

    CustomerMaintenanceService(CustomerRepositoryPort repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Observed(
            name = "customer.command",
            contextualName = "update-personal-data",
            lowCardinalityKeyValues = {"command", "update-personal-data"})
    public void updatePersonalData(TenantId tenant, CustomerId id, PersonalData personalData) {
        execute(tenant, id, "update-personal-data", c -> c.updatePersonalData(tenant, personalData, clock));
    }

    @Override
    @Observed(
            name = "customer.command",
            contextualName = "change-address",
            lowCardinalityKeyValues = {"command", "change-address"})
    public void changeAddress(TenantId tenant, CustomerId id, Address address) {
        execute(tenant, id, "change-address", c -> c.changeAddress(tenant, address, clock));
    }

    @Override
    @Observed(
            name = "customer.command",
            contextualName = "change-email",
            lowCardinalityKeyValues = {"command", "change-email"})
    public void changeEmail(TenantId tenant, CustomerId id, Email email) {
        execute(tenant, id, "change-email", c -> c.changeEmail(tenant, email, clock));
    }

    @Override
    @Observed(
            name = "customer.command",
            contextualName = "change-mobile-phone",
            lowCardinalityKeyValues = {"command", "change-mobile-phone"})
    public void changeMobilePhone(TenantId tenant, CustomerId id, MobilePhone mobilePhone) {
        execute(tenant, id, "change-mobile-phone", c -> c.changeMobilePhone(tenant, mobilePhone, clock));
    }

    private void execute(TenantId tenant, CustomerId id, String action, Consumer<Customer> change) {
        var customer = repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
        change.accept(customer);

        if (!customer.hasPendingEvents()) {
            log.atDebug()
                    .addKeyValue("customer.id", id)
                    .addKeyValue("customer.action", action)
                    .log("Nenhuma alteração: dados iguais aos atuais");
            return;
        }
        repository.save(customer);
        log.atInfo()
                .addKeyValue("customer.id", id)
                .addKeyValue("tenant.id", tenant)
                .addKeyValue("customer.action", action)
                .addKeyValue("customer.version", customer.version())
                .log("Cadastro do cliente alterado");
    }
}
