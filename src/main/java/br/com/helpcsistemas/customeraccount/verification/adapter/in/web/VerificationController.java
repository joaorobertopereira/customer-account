package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import br.com.helpcsistemas.customeraccount.shared.domain.Actor;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.ConfirmVerificationRequest;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.RequestVerificationRequest;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.VerificationResponse;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ConfirmVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.FindVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.RequestVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/verifications")
@Tag(name = "Validações")
class VerificationController {

    private final RequestVerificationUseCase requests;
    private final ConfirmVerificationUseCase confirmations;
    private final FindVerificationUseCase queries;

    VerificationController(
            RequestVerificationUseCase requests,
            ConfirmVerificationUseCase confirmations,
            FindVerificationUseCase queries) {
        this.requests = requests;
        this.confirmations = confirmations;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Executa a validação com o provedor configurado pelo tenant (com contingência automática)")
    ResponseEntity<VerificationResponse> request(
            @Parameter(hidden = true) TenantId tenant,
            @Parameter(hidden = true) Actor actor,
            @Valid @RequestBody RequestVerificationRequest request) {
        var view = requests.request(tenant, request.customerId(), request.type(), actor.id(), request.returnUrl());
        if (view.reused()) {
            return ResponseEntity.ok(VerificationWebMapper.toResponse(view));
        }
        return ResponseEntity.created(URI.create("/api/v1/verifications/" + view.id()))
                .body(VerificationWebMapper.toResponse(view));
    }

    @PostMapping("/{id}/confirmation")
    @Operation(summary = "Envia o código informado pelo titular (validações de posse: e-mail/celular)")
    VerificationResponse confirm(
            @Parameter(hidden = true) TenantId tenant,
            @PathVariable String id,
            @Valid @RequestBody ConfirmVerificationRequest request) {
        return VerificationWebMapper.toResponse(confirmations.confirm(tenant, VerificationId.of(id), request.code()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta o resultado padronizado de uma validação")
    VerificationResponse findById(@Parameter(hidden = true) TenantId tenant, @PathVariable String id) {
        return VerificationWebMapper.toResponse(queries.findById(tenant, VerificationId.of(id)));
    }

    @GetMapping
    @Operation(summary = "Lista as validações de um cliente do tenant (mais recentes primeiro)")
    List<VerificationResponse> findByCustomer(
            @Parameter(hidden = true) TenantId tenant, @RequestParam UUID customerId) {
        return queries.findByCustomer(tenant, customerId).stream()
                .map(VerificationWebMapper::toResponse)
                .toList();
    }
}
