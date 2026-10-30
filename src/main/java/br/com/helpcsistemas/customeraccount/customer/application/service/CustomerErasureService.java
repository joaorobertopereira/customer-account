package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.application.port.in.CompleteCustomerErasureUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.EraseCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CpfRegistryPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerErasureNotifierPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.PersonalDataErasurePort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import io.micrometer.observation.annotation.Observed;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Direito de eliminação (LGPD art. 18, VI) em Event Sourcing: eventos são imutáveis, então os dados são tornados
 * irrecuperáveis destruindo a chave do titular (crypto-shredding). O comando só grava {@code CustomerErased}; a
 * conclusão roda na subscription (at-least-once), garantindo que a chave seja destruída mesmo após falhas.
 */
@Service
class CustomerErasureService implements EraseCustomerUseCase, CompleteCustomerErasureUseCase {

    private static final Logger log = LoggerFactory.getLogger(CustomerErasureService.class);

    private final CustomerRepositoryPort repository;
    private final CpfRegistryPort cpfRegistry;
    private final PersonalDataErasurePort erasure;
    private final CustomerErasureNotifierPort notifier;
    private final Clock clock;

    CustomerErasureService(
            CustomerRepositoryPort repository,
            CpfRegistryPort cpfRegistry,
            PersonalDataErasurePort erasure,
            CustomerErasureNotifierPort notifier,
            Clock clock) {
        this.repository = repository;
        this.cpfRegistry = cpfRegistry;
        this.erasure = erasure;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Override
    @Observed(
            name = "customer.command",
            contextualName = "erase-customer",
            lowCardinalityKeyValues = {"command", "erase"})
    public void erase(TenantId tenant, CustomerId id) {
        var customer = repository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
        customer.erase(tenant, clock);
        repository.save(customer);
        log.atInfo()
                .addKeyValue("customer.id", id)
                .addKeyValue("tenant.id", tenant)
                .log("Eliminação de dados solicitada");
    }

    @Override
    public void completeErasure(CustomerId id) {
        // Ordem importa: o CPF precisa ser lido (decifrado) antes de a chave ser destruída.
        repository.findById(id).ifPresent(customer -> cpfRegistry.release(customer.cpf(), id));
        erasure.destroyKeys(id);
        notifier.erasureCompleted(id);
        log.atInfo().addKeyValue("customer.id", id).log("Eliminação concluída: CPF liberado e chave destruída");
    }
}
