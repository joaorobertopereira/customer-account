package br.com.helpcsistemas.customeraccount.customer.application.port.in;

import br.com.helpcsistemas.customeraccount.customer.application.query.CustomerView;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;

public interface FindCustomerUseCase {

    CustomerView findById(TenantId tenant, CustomerId id);

    CustomerView findByCpf(TenantId tenant, Cpf cpf);

    br.com.helpcsistemas.customeraccount.customer.application.query.AuthorizedCustomerView findReadableById(
            TenantId tenant, CustomerId id, java.util.Set<String> scopes);

    br.com.helpcsistemas.customeraccount.customer.application.query.AuthorizedCustomerView findReadableByCpf(
            TenantId tenant, Cpf cpf, java.util.Set<String> scopes);
}
