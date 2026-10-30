package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;

public interface PersonalDataErasurePort {

    /** Destrói a chave criptográfica do titular (crypto-shredding). Idempotente e irreversível. */
    void destroyKeys(CustomerId id);
}
