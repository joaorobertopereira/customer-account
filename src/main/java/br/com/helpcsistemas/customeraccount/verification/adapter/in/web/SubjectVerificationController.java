package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipal;
import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipalConverter;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.VerificationResponse;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.FindVerificationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/verifications")
@Tag(name = "Titular - autoatendimento")
class SubjectVerificationController {

    private static final Logger log = LoggerFactory.getLogger(SubjectVerificationController.class);

    private final CustomerDirectory customers;
    private final FindVerificationUseCase verifications;
    private final SubjectPrincipalConverter principalConverter = new SubjectPrincipalConverter();

    SubjectVerificationController(CustomerDirectory customers, FindVerificationUseCase verifications) {
        this.customers = customers;
        this.verifications = verifications;
    }

    @GetMapping
    @Operation(summary = "Lista somente as validações do titular autenticado")
    List<VerificationResponse> findOwnVerifications(@AuthenticationPrincipal Jwt jwt) {
        var principal = principalConverter.convert(jwt);
        var customerId = requireLinkedCustomer(principal);
        var result = verifications.findByCustomer(principal.tenantId(), customerId).stream()
                .map(VerificationWebMapper::toResponse)
                .toList();
        log.atInfo()
                .addKeyValue("audit.action", "subject.self-read")
                .addKeyValue("audit.resource", "verifications")
                .addKeyValue("customer.id", customerId)
                .addKeyValue("tenant.id", principal.tenantId())
                .log("Titular consultou as próprias validações");
        return result;
    }

    private UUID requireLinkedCustomer(SubjectPrincipal principal) {
        var customerId = principal.customerId();
        if (customerId == null) {
            throw new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado");
        }
        customers
                .findLinkedCustomer(principal.tenantId(), customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
        return customerId;
    }
}
