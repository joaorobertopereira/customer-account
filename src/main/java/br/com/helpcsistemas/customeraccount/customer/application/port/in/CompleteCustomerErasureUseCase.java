package br.com.helpcsistemas.customeraccount.customer.application.port.in;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;

/** Executado pela subscription de eliminação: libera o CPF e destrói a chave do titular. Idempotente. */
public interface CompleteCustomerErasureUseCase {

    void completeErasure(CustomerId id);
}
