package br.com.helpcsistemas.customeraccount.customer.application.service;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.helpcsistemas.customeraccount.customer.CustomerFixtures;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerErased;
import br.com.helpcsistemas.customeraccount.customer.domain.event.EmailChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerProjectionServiceTest {

    private final InMemoryCustomerViewRepository views = new InMemoryCustomerViewRepository();
    private final CustomerProjectionService projection = new CustomerProjectionService(views);
    private final CustomerId id = CustomerId.newId();

    @BeforeEach
    void registerCustomer() {
        var events = CustomerFixtures.newCustomer(id).pendingEvents();
        for (int revision = 0; revision < events.size(); revision++) {
            projection.project(events.get(revision), revision);
        }
    }

    @Test
    void projectsRegistrationAndTenantLink() {
        var view = views.findById(id).orElseThrow();

        assertThat(view.version()).isEqualTo(1);
        assertThat(view.tenants()).containsExactly(TENANT_A);
        assertThat(view.emailVerified()).isFalse();
        assertThat(view.cpf()).isEqualTo(CustomerFixtures.cpf());
    }

    @Test
    void isIdempotentForAlreadyAppliedRevisions() {
        var newEmail = Email.of("nova@example.com");
        var changed = new EmailChanged(id, TENANT_A, newEmail, CLOCK.instant());

        projection.project(changed, 2);
        projection.project(new EmailChanged(id, TENANT_A, Email.of("antiga@example.com"), CLOCK.instant()), 2);

        var view = views.findById(id).orElseThrow();
        assertThat(view.email()).isEqualTo(newEmail);
        assertThat(view.version()).isEqualTo(2);
    }

    @Test
    void removesViewOnErasure() {
        projection.project(new CustomerErased(id, TENANT_A, CLOCK.instant()), 2);

        assertThat(views.findById(id)).isEmpty();
    }
}
