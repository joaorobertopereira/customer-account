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

    CustomerQueryService(CustomerViewRepositoryPort views) {
        this.views = views;
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
        var view = result.filter(v -> v.visibleTo(tenant))
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
        log.atInfo()
                .addKeyValue("audit.action", "personal-data.read")
                .addKeyValue("audit.query", queryType)
                .addKeyValue("customer.id", view.id())
                .addKeyValue("tenant.id", tenant)
                .log("Dados pessoais consultados");
        return view;
    }
}
