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
    private final br.com.helpcsistemas.customeraccount.customer.CustomerDirectory directory =
            org.mockito.Mockito.mock(br.com.helpcsistemas.customeraccount.customer.CustomerDirectory.class);
    private final br.com.helpcsistemas.customeraccount.customer.CustomerDataAccess access =
            org.mockito.Mockito.mock(br.com.helpcsistemas.customeraccount.customer.CustomerDataAccess.class);
    private final CustomerQueryService queries = new CustomerQueryService(views, directory, access);
    private final CustomerId id = CustomerId.newId();

    @BeforeEach
    void projectCustomer() {
        org.mockito.Mockito.when(directory.findLinkedCustomer(TENANT_A, id.value()))
                .thenReturn(java.util.Optional.of(new br.com.helpcsistemas.customeraccount.customer.CustomerSnapshot(
                        id.value(),
                        CustomerFixtures.CPF,
                        "Maria da Silva Souza",
                        java.time.LocalDate.of(1990, 5, 20),
                        "maria.souza@example.com",
                        "+5511912345678")));
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

    @Test
    void intersectionNeverLeaksOtherFields() {
        org.mockito.Mockito.when(access.allowedFields(TENANT_A, id.value()))
                .thenReturn(java.util.Set.of("cpf", "email", "personalData.fullName"));
        assertThat(queries.findReadableById(TENANT_A, id, java.util.Set.of("customer:read:contact"))
                        .fields())
                .containsExactly("email");
        assertThat(queries.findReadableByCpf(TENANT_A, CustomerFixtures.cpf(), java.util.Set.of("customer:read"))
                        .fields())
                .containsExactlyInAnyOrder("cpf", "email", "personalData.fullName");
    }

    @Test
    void staleProjectionDoesNotRestoreUnlinkedAccess() {
        org.mockito.Mockito.when(directory.findLinkedCustomer(TENANT_A, id.value()))
                .thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> queries.findById(TENANT_A, id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
