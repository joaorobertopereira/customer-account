package br.com.helpcsistemas.customeraccount.consent.adapter.in.web;

import br.com.helpcsistemas.customeraccount.consent.application.service.ConsentService;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentGranted;
import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/consents")
class SubjectConsentController {
    private final ConsentService service;
    private final CustomerDirectory customers;

    SubjectConsentController(ConsentService service, CustomerDirectory customers) {
        this.service = service;
        this.customers = customers;
    }

    @GetMapping("/{target}")
    List<ConsentGranted> list(TenantId tenant, @AuthenticationPrincipal Jwt jwt, @PathVariable String target) {
        return service.list(TenantId.of(target), holder(tenant, jwt));
    }

    @PostMapping("/{target}")
    ResponseEntity<Void> grant(
            TenantId tenant,
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String target,
            @Valid @RequestBody ConsentController.GrantRequest request) {
        var id = holder(tenant, jwt);
        var evidence = new br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentEvidence(
                request.evidence().channel(),
                jwt.getClaimAsString("sid"),
                request.evidence().textHash());
        service.grant(TenantId.of(target), id, request.purpose(), request.policyVersion(), evidence);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{target}/{purpose}")
    ResponseEntity<Void> revoke(
            TenantId tenant,
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String target,
            @PathVariable String purpose,
            @Valid @RequestBody ConsentController.RevokeRequest request) {
        service.revoke(TenantId.of(target), holder(tenant, jwt), purpose, request.reason());
        return ResponseEntity.accepted().build();
    }

    private UUID holder(TenantId tenant, Jwt jwt) {
        var value = jwt.getClaimAsString("customer_id");
        if (value == null
                || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new org.springframework.security.access.AccessDeniedException("Titular não vinculado");
        }
        var id = UUID.fromString(value);
        customers
                .findLinkedCustomer(tenant, id)
                .orElseThrow(() -> new br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException(
                        "CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
        return id;
    }
}
