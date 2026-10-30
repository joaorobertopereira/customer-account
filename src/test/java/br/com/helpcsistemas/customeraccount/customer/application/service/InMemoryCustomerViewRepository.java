package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.application.query.CustomerView;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Fake em memória do read model, para testar a lógica de projeção/consulta sem MongoDB. */
class InMemoryCustomerViewRepository implements CustomerViewRepositoryPort {

    final Map<CustomerId, CustomerView> views = new HashMap<>();

    @Override
    public Optional<CustomerView> findById(CustomerId id) {
        return Optional.ofNullable(views.get(id));
    }

    @Override
    public Optional<CustomerView> findByCpf(Cpf cpf) {
        return views.values().stream().filter(v -> v.cpf().equals(cpf)).findFirst();
    }

    @Override
    public void save(CustomerView view) {
        views.put(view.id(), view);
    }

    @Override
    public void deleteById(CustomerId id) {
        views.remove(id);
    }
}
