package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Fake em memória do repositório de escrita, para testar serviços sem event store. */
class InMemoryCustomerRepository implements CustomerRepositoryPort {

    final Map<CustomerId, Customer> customers = new HashMap<>();

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return Optional.ofNullable(customers.get(id));
    }

    @Override
    public void save(Customer customer) {
        customers.put(customer.id(), customer);
    }
}
