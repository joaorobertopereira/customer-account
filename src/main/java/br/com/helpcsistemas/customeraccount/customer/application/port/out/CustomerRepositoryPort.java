package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import java.util.Optional;

public interface CustomerRepositoryPort {

    /** Vazio se não existir ou se os dados já foram eliminados (chave destruída). */
    Optional<Customer> findById(CustomerId id);

    /**
     * Grava os eventos pendentes com concorrência otimista.
     *
     * @throws br.com.helpcsistemas.customeraccount.shared.domain.ConcurrencyConflictException em escrita concorrente
     */
    void save(Customer customer);
}
