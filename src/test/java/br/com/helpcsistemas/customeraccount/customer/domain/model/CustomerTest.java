package br.com.helpcsistemas.customeraccount.customer.domain.model;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_A;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerErased;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkedToTenant;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerRegistered;
import br.com.helpcsistemas.customeraccount.customer.domain.event.EmailChanged;
import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private final CustomerId id = CustomerId.newId();

    @Test
    void registersAndLinksToTheRegisteringTenant() {
        var customer = CustomerFixtures.newCustomer(id);

        assertThat(customer.pendingEvents())
                .hasExactlyElementsOfTypes(CustomerRegistered.class, CustomerLinkedToTenant.class);
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.tenants()).containsExactly(TENANT_A);
        assertThat(customer.version()).isEqualTo(-1);
    }

    @Test
    void rejectsUnderage() {
        var data = new PersonalData(
                PersonName.of("Joao Pedro"), null, LocalDate.of(2010, 1, 1), null, null, CountryCode.BRAZIL);

        assertThatThrownBy(() -> Customer.register(
                        id,
                        TENANT_A,
                        CustomerFixtures.cpf(),
                        data,
                        CustomerFixtures.email(),
                        CustomerFixtures.mobilePhone(),
                        null,
                        CLOCK))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code")
                .isEqualTo("CUSTOMER_UNDERAGE");
    }

    @Test
    void rehydratesFromHistory() {
        var original = CustomerFixtures.newCustomer(id);
        var restored = Customer.rehydrate(original.pendingEvents(), 1);

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.cpf()).isEqualTo(CustomerFixtures.cpf());
        assertThat(restored.version()).isEqualTo(1);
        assertThat(restored.hasPendingEvents()).isFalse();
    }

    @Test
    void changesEmailOnlyWhenDifferent() {
        var customer = CustomerFixtures.persistedCustomer(id);

        customer.changeEmail(TENANT_A, CustomerFixtures.email(), CLOCK);
        assertThat(customer.hasPendingEvents()).isFalse();

        customer.changeEmail(TENANT_A, Email.of("nova@example.com"), CLOCK);
        assertThat(customer.pendingEvents()).hasExactlyElementsOfTypes(EmailChanged.class);
    }

    @Test
    void hidesCustomerFromTenantsNotLinked() {
        var customer = CustomerFixtures.persistedCustomer(id);

        assertThatThrownBy(() -> customer.changeAddress(TENANT_B, CustomerFixtures.address(), CLOCK))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void erasedCustomerCannotBeChanged() {
        var customer = CustomerFixtures.persistedCustomer(id);
        customer.erase(TENANT_A, CLOCK);

        assertThat(customer.pendingEvents()).hasExactlyElementsOfTypes(CustomerErased.class);
        assertThat(customer.status()).isEqualTo(CustomerStatus.ERASED);
        assertThatThrownBy(() -> customer.changeEmail(TENANT_A, Email.of("x@example.com"), CLOCK))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
