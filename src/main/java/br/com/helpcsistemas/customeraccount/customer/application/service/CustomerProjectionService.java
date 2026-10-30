package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.application.port.in.ProjectCustomerEventUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.application.query.CustomerView;
import br.com.helpcsistemas.customeraccount.customer.domain.event.AddressChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerErased;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerEvent;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkedToTenant;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerRegistered;
import br.com.helpcsistemas.customeraccount.customer.domain.event.EmailChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.MobilePhoneChanged;
import br.com.helpcsistemas.customeraccount.customer.domain.event.PersonalDataUpdated;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerStatus;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Lógica da projeção (independente de MongoDB): evento → read model. Idempotente por revisão. */
@Service
class CustomerProjectionService implements ProjectCustomerEventUseCase {

    private static final Logger log = LoggerFactory.getLogger(CustomerProjectionService.class);

    private final CustomerViewRepositoryPort views;

    CustomerProjectionService(CustomerViewRepositoryPort views) {
        this.views = views;
    }

    @Override
    public void project(CustomerEvent event, long revision) {
        switch (event) {
            case CustomerRegistered e -> create(e, revision);
            case CustomerErased e -> {
                views.deleteById(e.customerId());
                log.atInfo().addKeyValue("customer.id", e.customerId()).log("Read model removido (eliminação)");
            }
            default ->
                views.findById(event.customerId())
                        .filter(view -> revision > view.version())
                        .map(view -> apply(view, event, revision))
                        .ifPresentOrElse(views::save, () -> skipped(event, revision));
        }
    }

    private void create(CustomerRegistered e, long revision) {
        if (views.findById(e.customerId()).isPresent()) {
            skipped(e, revision);
            return;
        }
        views.save(new CustomerView(
                e.customerId(),
                revision,
                CustomerStatus.ACTIVE,
                e.cpf(),
                e.personalData(),
                e.email(),
                false,
                e.mobilePhone(),
                false,
                e.address(),
                Set.of(),
                e.occurredAt(),
                e.occurredAt()));
    }

    private static CustomerView apply(CustomerView view, CustomerEvent event, long revision) {
        return switch (event) {
            case CustomerLinkedToTenant e -> view.withTenant(e.tenantId(), revision, e.occurredAt());
            case PersonalDataUpdated e -> view.withPersonalData(e.personalData(), revision, e.occurredAt());
            case AddressChanged e -> view.withAddress(e.address(), revision, e.occurredAt());
            case EmailChanged e -> view.withEmail(e.email(), revision, e.occurredAt());
            case MobilePhoneChanged e -> view.withMobilePhone(e.mobilePhone(), revision, e.occurredAt());
            case CustomerRegistered _, CustomerErased _ -> view;
        };
    }

    private static void skipped(CustomerEvent event, long revision) {
        log.atDebug()
                .addKeyValue("customer.id", event.customerId())
                .addKeyValue("event.type", event.getClass().getSimpleName())
                .addKeyValue("event.revision", revision)
                .log("Evento já aplicado ou cliente inexistente no read model; ignorado");
    }
}
