package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.application.port.in.FindCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.CustomerViewRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.application.query.CustomerView;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import io.micrometer.observation.annotation.Observed;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Consultas no read model com isolamento por tenant. Cliente não vinculado ao tenant retorna 404 (não revela que o
 * CPF existe em outro tenant). Todo acesso a dados pessoais é logado para trilha de auditoria.
 */
@Service
class CustomerQueryService implements FindCustomerUseCase {

    private static final Logger log = LoggerFactory.getLogger(CustomerQueryService.class);

    private final CustomerViewRepositoryPort views;
    private final br.com.helpcsistemas.customeraccount.customer.CustomerDirectory directory;
    private final br.com.helpcsistemas.customeraccount.customer.CustomerDataAccess access;

    CustomerQueryService(
            CustomerViewRepositoryPort views,
            br.com.helpcsistemas.customeraccount.customer.CustomerDirectory directory,
            br.com.helpcsistemas.customeraccount.customer.CustomerDataAccess access) {
        this.views = views;
        this.directory = directory;
        this.access = access;
    }

    @Override
    @Observed(
            name = "customer.query",
            contextualName = "find-customer-by-id",
            lowCardinalityKeyValues = {"query", "by-id"})
    public CustomerView findById(TenantId tenant, CustomerId id) {
        return audited(tenant, "by-id", views.findById(id));
    }

    @Override
    @Observed(
            name = "customer.query",
            contextualName = "find-customer-by-cpf",
            lowCardinalityKeyValues = {"query", "by-cpf"})
    public CustomerView findByCpf(TenantId tenant, Cpf cpf) {
        return audited(tenant, "by-cpf", views.findByCpf(cpf));
    }

    private CustomerView audited(TenantId tenant, String queryType, Optional<CustomerView> result) {
        var view = resolve(tenant, result);
        audit(
                tenant,
                queryType,
                view,
                exposedFields(view, br.com.helpcsistemas.customeraccount.shared.domain.PersonalDataFields.ALL));
        return view;
    }

    @Override
    public br.com.helpcsistemas.customeraccount.customer.application.query.AuthorizedCustomerView findReadableById(
            TenantId tenant, CustomerId id, java.util.Set<String> scopes) {
        return readable(tenant, "by-id", views.findById(id), scopes);
    }

    @Override
    public br.com.helpcsistemas.customeraccount.customer.application.query.AuthorizedCustomerView findReadableByCpf(
            TenantId tenant, Cpf cpf, java.util.Set<String> scopes) {
        return readable(tenant, "by-cpf", views.findByCpf(cpf), scopes);
    }

    private CustomerView resolve(TenantId tenant, Optional<CustomerView> result) {
        return result.filter(v -> v.visibleTo(tenant))
                .filter(v ->
                        directory.findLinkedCustomer(tenant, v.id().value()).isPresent())
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
    }

    private br.com.helpcsistemas.customeraccount.customer.application.query.AuthorizedCustomerView readable(
            TenantId tenant, String query, Optional<CustomerView> result, java.util.Set<String> scopes) {
        var view = resolve(tenant, result);
        var fields =
                new java.util.HashSet<>(access.allowedFields(tenant, view.id().value()));
        fields.retainAll(br.com.helpcsistemas.customeraccount.shared.domain.PersonalDataFields.forScopes(scopes));
        fields = exposedFields(view, fields);
        audit(tenant, query, view, fields);
        return new br.com.helpcsistemas.customeraccount.customer.application.query.AuthorizedCustomerView(view, fields);
    }

    private static java.util.HashSet<String> exposedFields(CustomerView view, java.util.Set<String> permitted) {
        var fields = new java.util.HashSet<>(permitted);
        if (view.address() == null) {
            fields.remove("address");
        }
        if (view.personalData().socialName() == null) {
            fields.remove("personalData.socialName");
        }
        if (view.personalData().motherName() == null) {
            fields.remove("personalData.motherName");
        }
        if (view.personalData().maritalStatus() == null) {
            fields.remove("personalData.maritalStatus");
        }
        return fields;
    }

    private static void audit(TenantId tenant, String queryType, CustomerView view, java.util.Set<String> fields) {
        log.atInfo()
                .addKeyValue("audit.action", "personal-data.read")
                .addKeyValue("audit.query", queryType)
                .addKeyValue("customer.id", view.id())
                .addKeyValue("tenant.id", tenant)
                .addKeyValue("audit.fields", fields.stream().sorted().toList())
                .log("Dados pessoais consultados");
    }
}
