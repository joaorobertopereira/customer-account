package br.com.helpcsistemas.customeraccount.customer.application.port.in;

import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerEvent;

/** Atualiza o read model a partir de um evento. Idempotente: ignora revisões já aplicadas. */
public interface ProjectCustomerEventUseCase {

    void project(CustomerEvent event, long revision);
}
