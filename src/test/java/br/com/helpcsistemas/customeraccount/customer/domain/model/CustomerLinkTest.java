package br.com.helpcsistemas.customeraccount.customer.domain.model;

import static br.com.helpcsistemas.customeraccount.customer.CustomerFixtures.*;
import static org.assertj.core.api.Assertions.*;

import br.com.helpcsistemas.customeraccount.customer.domain.event.*;
import br.com.helpcsistemas.customeraccount.shared.domain.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class CustomerLinkTest {
    @Test
    void requestDoesNotLinkOrOverwriteAndRequiresApproval() {
        var customer = persistedCustomer(CustomerId.newId());
        customer.requestLink(TENANT_B, personalData(), Email.of("other@example.com"), mobilePhone(), address(), CLOCK);
        var request = customer.linkRequests().get(TENANT_B);
        assertThat(customer.tenants()).doesNotContain(TENANT_B);
        assertThat(customer.email()).isEqualTo(email());
        assertThat(request.expiresAt()).isEqualTo(CLOCK.instant().plus(Duration.ofDays(7)));
        customer.requestLink(TENANT_B, personalData(), email(), mobilePhone(), address(), CLOCK);
        assertThat(customer.pendingEvents()).hasSize(1);
        assertThatThrownBy(() -> customer.decideLink(TENANT_B, request.requestId(), true, CLOCK))
                .isInstanceOf(ResourceNotFoundException.class);
        customer.decideLink(TENANT_A, request.requestId(), true, CLOCK);
        assertThat(customer.tenants()).contains(TENANT_A, TENANT_B);
        assertThat(customer.linkRequests()).isEmpty();
        assertThat(customer.pendingEvents()).hasSize(3);
        customer.unlink(TENANT_A, TENANT_B, CLOCK);
        assertThat(customer.tenants()).containsExactly(TENANT_A);
        assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(customer.linkedAt(TENANT_B)).isEmpty();
    }

    @Test
    void expirationIsStrictAndRejectedEventIsPersistable() {
        var customer = persistedCustomer(CustomerId.newId());
        customer.requestLink(TENANT_B, personalData(), email(), mobilePhone(), address(), CLOCK);
        var request = customer.linkRequests().get(TENANT_B);
        var expired = Clock.fixed(request.expiresAt(), ZoneOffset.UTC);
        assertThatThrownBy(() -> customer.decideLink(TENANT_A, request.requestId(), true, expired))
                .isInstanceOf(BusinessRuleException.class);
        customer.expireLink(request.requestId(), expired);
        assertThat(customer.linkRequests()).isEmpty();
        assertThat(customer.pendingEvents().getLast()).isInstanceOf(CustomerLinkRejected.class);
        assertThat(customer.tenants()).doesNotContain(TENANT_B);
    }

    @Test
    void holderCanRejectAndUnknownRequestsDoNotRevealCustomers() {
        var customer = persistedCustomer(CustomerId.newId());
        customer.requestLink(TENANT_B, personalData(), email(), mobilePhone(), address(), CLOCK);
        customer.decideLink(TENANT_A, customer.linkRequests().get(TENANT_B).requestId(), false, CLOCK);
        assertThat(customer.tenants()).doesNotContain(TENANT_B);
        assertThatThrownBy(() -> customer.decideLink(TENANT_A, java.util.UUID.randomUUID(), true, CLOCK))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
