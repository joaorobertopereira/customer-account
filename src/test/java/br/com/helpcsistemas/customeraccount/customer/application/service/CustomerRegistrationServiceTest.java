package br.com.helpcsistemas.customeraccount.customer.application.service;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.RegisterCustomerCommand;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CpfRegistryPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Customer;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceAlreadyExistsException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerRegistrationServiceTest {

    private final CustomerRepositoryPort repository = mock(CustomerRepositoryPort.class);
    private final CpfRegistryPort cpfRegistry = mock(CpfRegistryPort.class);
    private final CustomerRegistrationService service = new CustomerRegistrationService(repository, cpfRegistry, CLOCK);

    private final RegisterCustomerCommand command = new RegisterCustomerCommand(
            TENANT_A,
            CustomerFixtures.cpf(),
            CustomerFixtures.personalData(),
            CustomerFixtures.email(),
            CustomerFixtures.mobilePhone(),
            CustomerFixtures.address());

    @Test
    void reservesCpfAndPersistsCustomer() {
        when(cpfRegistry.reserve(eq(CustomerFixtures.cpf()), any())).thenReturn(true);

        var id = service.register(command);

        var saved = ArgumentCaptor.forClass(Customer.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(id);
        assertThat(saved.getValue().pendingEvents()).hasSize(2);
    }

    @Test
    void rejectsDuplicatedCpf() {
        when(cpfRegistry.reserve(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.register(command))
                .isInstanceOf(ResourceAlreadyExistsException.class)
                .extracting("code")
                .isEqualTo("CUSTOMER_ALREADY_REGISTERED");
        verify(repository, never()).save(any());
    }

    @Test
    void releasesCpfWhenPersistenceFails() {
        when(cpfRegistry.reserve(any(), any())).thenReturn(true);
        doThrow(new IllegalStateException("event store fora")).when(repository).save(any());

        assertThatThrownBy(() -> service.register(command)).isInstanceOf(IllegalStateException.class);
        verify(cpfRegistry).release(eq(CustomerFixtures.cpf()), any(CustomerId.class));
    }
}
