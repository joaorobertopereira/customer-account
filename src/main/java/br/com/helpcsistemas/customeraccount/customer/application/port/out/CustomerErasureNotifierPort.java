package br.com.helpcsistemas.customeraccount.customer.application.port.out;

import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;

public interface CustomerErasureNotifierPort {

    void erasureCompleted(CustomerId id);
}
