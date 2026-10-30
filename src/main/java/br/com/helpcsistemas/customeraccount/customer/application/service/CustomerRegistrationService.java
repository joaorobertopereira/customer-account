package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.application.port.in.RegisterCustomerCommand;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.RegisterCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CpfRegistryPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceAlreadyExistsException;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cadastro: valida o agregado, reserva o CPF (unicidade global) e grava os eventos. Se a gravação falhar, a reserva
 * é desfeita (compensação), pois não há transação distribuída entre os dois streams.
 */
@Service
class CustomerRegistrationService implements RegisterCustomerUseCase {

    private static final Logger log = LoggerFactory.getLogger(CustomerRegistrationService.class);

    private final CustomerRepositoryPort repository;
    private final CpfRegistryPort cpfRegistry;
    private final Clock clock;

    CustomerRegistrationService(CustomerRepositoryPort repository, CpfRegistryPort cpfRegistry, Clock clock) {
        this.repository = repository;
        this.cpfRegistry = cpfRegistry;
        this.clock = clock;
    }

    @Override
    @Observed(
            name = "customer.command",
            contextualName = "register-customer",
            lowCardinalityKeyValues = {"command", "register"})
    public CustomerId register(RegisterCustomerCommand command) {
        var id = CustomerId.newId();
        var customer = Customer.register(
                id,
                command.tenantId(),
                command.cpf(),
                command.personalData(),
                command.email(),
                command.mobilePhone(),
                command.address(),
                clock);

        if (!cpfRegistry.reserve(command.cpf(), id)) {
            log.atWarn().addKeyValue("tenant.id", command.tenantId()).log("Cadastro recusado: CPF já registrado");
            throw new ResourceAlreadyExistsException(
                    "CUSTOMER_ALREADY_REGISTERED", "Já existe cadastro para o CPF informado");
        }

        try {
            repository.save(customer);
        } catch (RuntimeException ex) {
            log.atError()
                    .addKeyValue("customer.id", id)
                    .setCause(ex)
                    .log("Falha ao gravar cadastro; liberando reserva de CPF");
            cpfRegistry.release(command.cpf(), id);
            throw ex;
        }

        log.atInfo()
                .addKeyValue("customer.id", id)
                .addKeyValue("tenant.id", command.tenantId())
                .log("Cliente registrado");
        return id;
    }
}
