package br.com.helpcsistemas.customeraccount.customer.application.port.in;

import br.com.helpcsistemas.customeraccount.customer.application.query.CustomerView;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;

public interface FindCustomerUseCase {

    CustomerView findById(TenantId tenant, CustomerId id);

    CustomerView findByCpf(TenantId tenant, Cpf cpf);
}
