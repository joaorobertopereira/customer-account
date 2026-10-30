package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import br.com.helpcsistemas.customeraccount.customer.application.query.CustomerView;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import java.util.Optional;

/** Read model (lado de consulta do CQRS). */
public interface CustomerViewRepositoryPort {

    Optional<CustomerView> findById(CustomerId id);

    Optional<CustomerView> findByCpf(Cpf cpf);

    void save(CustomerView view);

    void deleteById(CustomerId id);
}
