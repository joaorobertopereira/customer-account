package br.com.helpcsistemas.customeraccount.customer.application.port.in;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;

public interface EraseCustomerUseCase {

    /** Registra a solicitação de eliminação; a destruição das chaves ocorre de forma assíncrona e garantida. */
    void erase(TenantId tenant, CustomerId id);
}
