package br.com.helpcsistemas.customeraccount.customer.application.service;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_A;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_B;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CpfRegistryPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerErasureNotifierPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.PersonalDataErasurePort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerErasureServiceTest {

    private final CustomerRepositoryPort repository = mock(CustomerRepositoryPort.class);
    private final CpfRegistryPort cpfRegistry = mock(CpfRegistryPort.class);
    private final PersonalDataErasurePort erasure = mock(PersonalDataErasurePort.class);
    private final CustomerErasureNotifierPort notifier = mock(CustomerErasureNotifierPort.class);
    private final CustomerErasureService service =
            new CustomerErasureService(repository, cpfRegistry, erasure, notifier, CLOCK);
    private final CustomerId id = CustomerId.newId();

    @Test
    void erasureRequestOnlyRecordsTheEvent() {
        when(repository.findById(id)).thenReturn(Optional.of(CustomerFixtures.persistedCustomer(id)));

        service.erase(TENANT_A, id);

        verify(repository).save(any());
        verify(erasure, never()).destroyKeys(any());
    }

    @Test
    void tenantNotLinkedCannotErase() {
        when(repository.findById(id)).thenReturn(Optional.of(CustomerFixtures.persistedCustomer(id)));

        assertThatThrownBy(() -> service.erase(TENANT_B, id)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void completionReleasesCpfBeforeDestroyingKeys() {
        when(repository.findById(id)).thenReturn(Optional.of(CustomerFixtures.persistedCustomer(id)));

        service.completeErasure(id);

        var order = inOrder(cpfRegistry, erasure);
        order.verify(cpfRegistry).release(CustomerFixtures.cpf(), id);
        order.verify(erasure).destroyKeys(id);
    }

    @Test
    void completionIsIdempotentWhenKeysAreAlreadyGone() {
        when(repository.findById(id)).thenReturn(Optional.empty());

        service.completeErasure(id);

        verify(cpfRegistry, never()).release(any(), any());
        verify(erasure).destroyKeys(id);
    }
}
