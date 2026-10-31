package br.com.helpcsistemas.customeraccount.consent.adapter.in.web;

import br.com.helpcsistemas.customeraccount.consent.application.service.ConsentService;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentGranted;
import br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentEvidence;
import br.com.helpcsistemas.customeraccount.consent.domain.model.Purpose;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ConsentController {
    private final ConsentService service;

    ConsentController(ConsentService service) {
        this.service = service;
    }

    @PutMapping("/api/v1/tenant/purposes/{code}")
    ResponseEntity<Void> put(TenantId tenant, @PathVariable String code, @Valid @RequestBody PurposeRequest request) {
        service.putPurpose(
                tenant,
                new Purpose(
                        code, request.description(), request.legalBasis(), request.fields(), request.policyVersion()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/tenant/purposes")
    List<Purpose> purposes(TenantId tenant) {
        return service.purposes(tenant);
    }

    @PostMapping("/api/v1/customers/{id}/consents")
    ResponseEntity<Void> grant(TenantId tenant, @PathVariable UUID id, @Valid @RequestBody GrantRequest request) {
        service.grant(tenant, id, request.purpose(), request.policyVersion(), request.evidence());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/v1/customers/{id}/consents")
    List<ConsentGranted> list(TenantId tenant, @PathVariable UUID id) {
        return service.list(tenant, id);
    }

    @DeleteMapping("/api/v1/customers/{id}/consents/{purpose}")
    ResponseEntity<Void> revoke(
            TenantId tenant,
            @PathVariable UUID id,
            @PathVariable String purpose,
            @Valid @RequestBody RevokeRequest request) {
        service.revoke(tenant, id, purpose, request.reason());
        return ResponseEntity.accepted().build();
    }

    record PurposeRequest(
            @NotBlank String description,
            @NotNull Purpose.LegalBasis legalBasis,
            @NotNull Set<String> fields,
            @NotBlank String policyVersion) {}

    record GrantRequest(
            @NotBlank String purpose,
            @NotBlank String policyVersion,
            @NotNull @Valid ConsentEvidence evidence) {}

    record RevokeRequest(@NotBlank String reason) {}
}
