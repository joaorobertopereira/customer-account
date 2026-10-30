package br.com.helpcsistemas.customeraccount.customer.adapter.in.web;

import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.AddressRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.ChangeEmailRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.ChangeMobilePhoneRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.PersonalDataRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.RegisterCustomerRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerResponses.RegisteredResponse;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.EraseCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.RegisterCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.UpdateCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lado de comando (CQRS). Alterações retornam 202: o read model é atualizado de forma assíncrona pela projeção.
 * O tenant vem sempre do token (parâmetro {@link TenantId} resolvido pela plataforma).
 */
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers - comandos")
class CustomerCommandController {

    private final RegisterCustomerUseCase registration;
    private final UpdateCustomerUseCase maintenance;
    private final EraseCustomerUseCase erasure;

    CustomerCommandController(
            RegisterCustomerUseCase registration, UpdateCustomerUseCase maintenance, EraseCustomerUseCase erasure) {
        this.registration = registration;
        this.maintenance = maintenance;
        this.erasure = erasure;
    }

    @PostMapping
    @Operation(summary = "Cadastra um cliente e o vincula ao tenant")
    ResponseEntity<RegisteredResponse> register(
            @Parameter(hidden = true) TenantId tenant, @Valid @RequestBody RegisterCustomerRequest request) {
        var id = registration.register(CustomerWebMapper.toCommand(tenant, request));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + id))
                .body(new RegisteredResponse(id.toString()));
    }

    @PutMapping("/{id}/personal-data")
    @Operation(summary = "Atualiza dados pessoais")
    ResponseEntity<Void> updatePersonalData(
            @Parameter(hidden = true) TenantId tenant,
            @PathVariable String id,
            @Valid @RequestBody PersonalDataRequest request) {
        maintenance.updatePersonalData(tenant, CustomerId.of(id), CustomerWebMapper.toPersonalData(request));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/{id}/address")
    @Operation(summary = "Altera o endereço")
    ResponseEntity<Void> changeAddress(
            @Parameter(hidden = true) TenantId tenant,
            @PathVariable String id,
            @Valid @RequestBody AddressRequest request) {
        maintenance.changeAddress(tenant, CustomerId.of(id), CustomerWebMapper.toAddress(request));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/{id}/email")
    @Operation(summary = "Altera o e-mail (volta a ficar não verificado)")
    ResponseEntity<Void> changeEmail(
            @Parameter(hidden = true) TenantId tenant,
            @PathVariable String id,
            @Valid @RequestBody ChangeEmailRequest request) {
        maintenance.changeEmail(tenant, CustomerId.of(id), Email.of(request.email()));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/{id}/mobile-phone")
    @Operation(summary = "Altera o celular (volta a ficar não verificado)")
    ResponseEntity<Void> changeMobilePhone(
            @Parameter(hidden = true) TenantId tenant,
            @PathVariable String id,
            @Valid @RequestBody ChangeMobilePhoneRequest request) {
        maintenance.changeMobilePhone(tenant, CustomerId.of(id), MobilePhone.of(request.mobilePhone()));
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Elimina os dados do titular (LGPD art. 18, VI) via crypto-shredding")
    ResponseEntity<Void> erase(@Parameter(hidden = true) TenantId tenant, @PathVariable String id) {
        erasure.erase(tenant, CustomerId.of(id));
        return ResponseEntity.accepted().build();
    }
}
