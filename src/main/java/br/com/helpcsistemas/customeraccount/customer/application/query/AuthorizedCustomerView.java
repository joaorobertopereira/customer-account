package br.com.helpcsistemas.customeraccount.customer.application.query;

import java.util.Set;

public record AuthorizedCustomerView(CustomerView customer, Set<String> fields) {
    public AuthorizedCustomerView {
        fields = Set.copyOf(fields);
    }
}
