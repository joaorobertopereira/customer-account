package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;

/** Garante unicidade global de CPF no lado de escrita (o read model é eventual e não serve para isso). */
public interface CpfRegistryPort {

    /** @return false se o CPF já pertence a outro cliente */
    boolean reserve(Cpf cpf, CustomerId owner);

    java.util.Optional<CustomerId> findOwner(Cpf cpf);

    /** Libera o CPF se ainda reservado para {@code owner}. Idempotente. */
    void release(Cpf cpf, CustomerId owner);
}
