package br.com.helpcsistemas.customeraccount.customer.application.port.in;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;

public interface RegisterCustomerUseCase {

    CustomerId register(RegisterCustomerCommand command);
}
