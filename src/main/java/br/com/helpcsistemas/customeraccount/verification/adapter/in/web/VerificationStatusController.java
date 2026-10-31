package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.FindVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationOutcome;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/verification-status")
class VerificationStatusController {

    private final FindVerificationUseCase verifications;
    private final Clock clock;

    VerificationStatusController(FindVerificationUseCase verifications, Clock clock) {
        this.verifications = verifications;
        this.clock = clock;
    }

    @GetMapping
    CustomerVerificationStatus find(
            @PathVariable UUID customerId, @RequestParam VerificationType type, TenantId tenant) {
        var latest = verifications.findByCustomer(tenant, customerId).stream()
                .filter(view -> view.type() == type)
                .findFirst();
        if (latest.isEmpty()) {
            return new CustomerVerificationStatus(customerId, type, "NONE", null, null, null);
        }
        var view = latest.get();
        var status = view.status() != VerificationStatus.COMPLETED
                ? "PENDING"
                : view.outcome() == VerificationOutcome.APPROVED
                        ? view.validUntil() == null || clock.instant().isBefore(view.validUntil()) ? "VALID" : "EXPIRED"
                        : view.outcome() == VerificationOutcome.EXPIRED ? "EXPIRED" : "NOT_APPROVED";
        return new CustomerVerificationStatus(
                customerId, type, status, view.id().toString(), view.outcome(), view.validUntil());
    }

    record CustomerVerificationStatus(
            UUID customerId,
            VerificationType type,
            String status,
            String verificationId,
            VerificationOutcome outcome,
            Instant validUntil) {}
}
