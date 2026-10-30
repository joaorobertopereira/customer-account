package br.com.helpcsistemas.customeraccount.customer.domain.model;

import br.com.helpcsistemas.customeraccount.customer.domain.event.AddressChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerErased;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerEvent;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkedToTenant;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerRegistered;
import br.com.helpcsistemas.customeraccount.customer.domain.event.EmailChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.MobilePhoneChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.PersonalDataUpdated;
import br.com.helpcsistemas.customeraccount.shared.domain.EventSourcedAggregate;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Agregado raiz: a pessoa (titular dos dados). Estado reconstruído a partir dos eventos. Um tenant só pode alterar
 * clientes vinculados a ele; para os demais o cliente "não existe" (não revela cadastro de outro tenant).
 */
public class Customer extends EventSourcedAggregate<CustomerEvent> {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");

    private CustomerId id;
    private CustomerStatus status;
    private Cpf cpf;
    private PersonalData personalData;
    private Email email;
    private MobilePhone mobilePhone;
    private Address address;
    private final Set<TenantId> tenants = new HashSet<>();

    private Customer() {}

    public static Customer register(
            CustomerId id,
            TenantId tenant,
            Cpf cpf,
            PersonalData personalData,
            Email email,
            MobilePhone mobilePhone,
            Address address,
            Clock clock) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(cpf, "cpf");
        Objects.requireNonNull(personalData, "personalData");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(mobilePhone, "mobilePhone");
        personalData.ensureEligibleOn(today(clock));

        var now = clock.instant();
        var customer = new Customer();
        customer.raise(new CustomerRegistered(id, tenant, cpf, personalData, email, mobilePhone, address, now));
        customer.raise(new CustomerLinkedToTenant(id, tenant, now));
        return customer;
    }

    public static Customer rehydrate(List<CustomerEvent> history, long version) {
        if (history.isEmpty() || !(history.getFirst() instanceof CustomerRegistered)) {
            throw new IllegalArgumentException("Histórico deve começar com CustomerRegistered");
        }
        var customer = new Customer();
        customer.replay(history, version);
        return customer;
    }

    public void updatePersonalData(TenantId by, PersonalData newData, Clock clock) {
        ensureActiveFor(by);
        newData.ensureEligibleOn(today(clock));
        if (!newData.equals(personalData)) {
            raise(new PersonalDataUpdated(id, by, newData, clock.instant()));
        }
    }

    public void changeAddress(TenantId by, Address newAddress, Clock clock) {
        ensureActiveFor(by);
        Objects.requireNonNull(newAddress, "address");
        if (!newAddress.equals(address)) {
            raise(new AddressChanged(id, by, newAddress, clock.instant()));
        }
    }

    public void changeEmail(TenantId by, Email newEmail, Clock clock) {
        ensureActiveFor(by);
        Objects.requireNonNull(newEmail, "email");
        if (!newEmail.equals(email)) {
            raise(new EmailChanged(id, by, newEmail, clock.instant()));
        }
    }

    public void changeMobilePhone(TenantId by, MobilePhone newPhone, Clock clock) {
        ensureActiveFor(by);
        Objects.requireNonNull(newPhone, "mobilePhone");
        if (!newPhone.equals(mobilePhone)) {
            raise(new MobilePhoneChanged(id, by, newPhone, clock.instant()));
        }
    }

    /** Eliminação global (todos os tenants). Revogação por tenant será tratada no módulo de consentimento. */
    public void erase(TenantId by, Clock clock) {
        ensureActiveFor(by);
        raise(new CustomerErased(id, by, clock.instant()));
    }

    @Override
    protected void apply(CustomerEvent event) {
        switch (event) {
            case CustomerRegistered e -> {
                id = e.customerId();
                status = CustomerStatus.ACTIVE;
                cpf = e.cpf();
                personalData = e.personalData();
                email = e.email();
                mobilePhone = e.mobilePhone();
                address = e.address();
            }
            case CustomerLinkedToTenant e -> tenants.add(e.tenantId());
            case PersonalDataUpdated e -> personalData = e.personalData();
            case AddressChanged e -> address = e.address();
            case EmailChanged e -> email = e.email();
            case MobilePhoneChanged e -> mobilePhone = e.mobilePhone();
            case CustomerErased _ -> status = CustomerStatus.ERASED;
        }
    }

    private void ensureActiveFor(TenantId tenant) {
        if (status != CustomerStatus.ACTIVE || !tenants.contains(tenant)) {
            throw new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado");
        }
    }

    private static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE);
    }

    public CustomerId id() {
        return id;
    }

    public CustomerStatus status() {
        return status;
    }

    public Cpf cpf() {
        return cpf;
    }

    public PersonalData personalData() {
        return personalData;
    }

    public Email email() {
        return email;
    }

    public MobilePhone mobilePhone() {
        return mobilePhone;
    }

    public Optional<Address> address() {
        return Optional.ofNullable(address);
    }

    public Set<TenantId> tenants() {
        return Set.copyOf(tenants);
    }
}
