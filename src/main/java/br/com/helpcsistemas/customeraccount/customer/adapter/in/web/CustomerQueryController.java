package br.com.helpcsistemas.customeraccount.customer.adapter.in.web;

import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.SearchByCpfRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerResponses.CustomerResponse;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.FindCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Cpf;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lado de consulta (CQRS): lê somente o read model do MongoDB. */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers - consultas")
class CustomerQueryController {

    private final FindCustomerUseCase find;

    CustomerQueryController(FindCustomerUseCase find) {
        this.find = find;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um cliente vinculado ao tenant")
    CustomerResponse findById(@Parameter(hidden = true) TenantId tenant, @PathVariable String id) {
        return CustomerWebMapper.toResponse(find.findById(tenant, CustomerId.of(id)));
    }

    @PostMapping("/search")
    @Operation(summary = "Busca cliente por CPF (CPF no corpo, nunca na URL)")
    CustomerResponse searchByCpf(
            @Parameter(hidden = true) TenantId tenant, @Valid @RequestBody SearchByCpfRequest request) {
        return CustomerWebMapper.toResponse(find.findByCpf(tenant, Cpf.of(request.cpf())));
    }
}
