package br.com.helpcsistemas.customeraccount.customer.application.service;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_A;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerQueryServiceTest {

    private final InMemoryCustomerViewRepository views = new InMemoryCustomerViewRepository();
    private final CustomerQueryService queries = new CustomerQueryService(views);
    private final CustomerId id = CustomerId.newId();

    @BeforeEach
    void projectCustomer() {
        var projection = new CustomerProjectionService(views);
        var events = CustomerFixtures.newCustomer(id).pendingEvents();
        for (int revision = 0; revision < events.size(); revision++) {
            projection.project(events.get(revision), revision);
        }
    }

    @Test
    void linkedTenantReadsCustomer() {
        assertThat(queries.findById(TENANT_A, id).id()).isEqualTo(id);
        assertThat(queries.findByCpf(TENANT_A, CustomerFixtures.cpf()).id()).isEqualTo(id);
    }

    @Test
    void otherTenantDoesNotSeeCustomer() {
        assertThatThrownBy(() -> queries.findById(TENANT_B, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> queries.findByCpf(TENANT_B, CustomerFixtures.cpf()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
