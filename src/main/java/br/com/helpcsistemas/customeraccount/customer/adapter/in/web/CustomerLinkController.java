package br.com.helpcsistemas.customeraccount.customer.adapter.in.web;

import br.com.helpcsistemas.customeraccount.customer.application.service.CustomerLinkService;
import br.com.helpcsistemas.customeraccount.customer.domain.event.CustomerLinkRequested;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomerLinkController {
    private final CustomerLinkService links;

    CustomerLinkController(CustomerLinkService links) {
        this.links = links;
    }

    @DeleteMapping("/api/v1/customers/{id}/link")
    ResponseEntity<Void> unlink(TenantId tenant, @PathVariable UUID id) {
        links.unlink(tenant, id, tenant);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/v1/me/links/requests")
    List<CustomerLinkRequested> pending(TenantId tenant, @AuthenticationPrincipal Jwt jwt) {
        return links.requests(tenant, holder(jwt));
    }

    @GetMapping("/api/v1/me/links/suggestions")
    List<CustomerLinkRequested> suggestions(TenantId tenant, @AuthenticationPrincipal Jwt jwt) {
        return links.suggestions(tenant, holder(jwt));
    }

    @PostMapping("/api/v1/me/links/requests/{requestId}/approve")
    ResponseEntity<Void> approve(TenantId tenant, @AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        links.decide(tenant, holder(jwt), requestId, true);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/me/links/requests/{requestId}/reject")
    ResponseEntity<Void> reject(TenantId tenant, @AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        links.decide(tenant, holder(jwt), requestId, false);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/api/v1/me/links/{target}")
    ResponseEntity<Void> unlinkSelf(TenantId tenant, @AuthenticationPrincipal Jwt jwt, @PathVariable String target) {
        links.unlink(tenant, holder(jwt), TenantId.of(target));
        return ResponseEntity.accepted().build();
    }

    private static UUID holder(Jwt jwt) {
        var claim = jwt.getClaimAsString("customer_id");
        if (claim == null
                || !claim.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new org.springframework.security.access.AccessDeniedException("Titular não vinculado");
        }
        return UUID.fromString(claim);
    }
}
