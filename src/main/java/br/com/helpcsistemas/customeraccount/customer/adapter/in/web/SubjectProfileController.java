package br.com.helpcsistemas.customeraccount.customer.adapter.in.web;

import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.AddressRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.ChangeEmailRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.ChangeMobilePhoneRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerRequests.PersonalDataRequest;
import br.com.helpcsistemas.customeraccount.customer.adapter.in.web.CustomerResponses.CustomerResponse;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.FindCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.port.in.UpdateCustomerUseCase;
import br.com.helpcsistemas.customeraccount.customer.application.service.SubjectLinkService;
import br.com.helpcsistemas.customeraccount.customer.domain.model.CustomerId;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import br.com.helpcsistemas.customeraccount.customer.domain.model.MobilePhone;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipal;
import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipalConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operações de autoatendimento limitadas ao subject autenticado e ao vínculo atual do tenant. */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Titular - autoatendimento")
class SubjectProfileController {

    private static final Logger log = LoggerFactory.getLogger(SubjectProfileController.class);

    private final CustomerDirectory directory;
    private final SubjectLinkService links;
    private final FindCustomerUseCase queries;
    private final UpdateCustomerUseCase updates;
    private final SubjectPrincipalConverter principalConverter = new SubjectPrincipalConverter();

    SubjectProfileController(
            CustomerDirectory directory,
            SubjectLinkService links,
            FindCustomerUseCase queries,
            UpdateCustomerUseCase updates) {
        this.directory = directory;
        this.links = links;
        this.queries = queries;
        this.updates = updates;
    }

    @GetMapping
    @Operation(summary = "Lê o perfil do próprio titular")
    CustomerResponse profile(@AuthenticationPrincipal Jwt jwt) {
        var principal = principalConverter.convert(jwt);
        var customerId = requireLinkedCustomer(principal);
        var response = CustomerWebMapper.toResponse(queries.findById(principal.tenantId(), new CustomerId(customerId)));
        logSelfRead(principal, customerId, "profile");
        return response;
    }

    @PutMapping("/personal-data")
    @Operation(summary = "Atualiza os dados pessoais do próprio titular")
    ResponseEntity<Void> updatePersonalData(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PersonalDataRequest request) {
        var principal = principalConverter.convert(jwt);
        var customerId = requireLinkedCustomer(principal);
        updates.updatePersonalData(
                principal.tenantId(), new CustomerId(customerId), CustomerWebMapper.toPersonalData(request));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/address")
    @Operation(summary = "Atualiza o endereço do próprio titular")
    ResponseEntity<Void> updateAddress(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        var principal = principalConverter.convert(jwt);
        var customerId = requireLinkedCustomer(principal);
        updates.changeAddress(principal.tenantId(), new CustomerId(customerId), CustomerWebMapper.toAddress(request));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/email")
    @Operation(summary = "Atualiza o e-mail do próprio titular")
    ResponseEntity<Void> updateEmail(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeEmailRequest request) {
        var principal = principalConverter.convert(jwt);
        var customerId = requireLinkedCustomer(principal);
        updates.changeEmail(principal.tenantId(), new CustomerId(customerId), Email.of(request.email()));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/mobile-phone")
    @Operation(summary = "Atualiza o celular do próprio titular")
    ResponseEntity<Void> updateMobilePhone(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeMobilePhoneRequest request) {
        var principal = principalConverter.convert(jwt);
        var customerId = requireLinkedCustomer(principal);
        updates.changeMobilePhone(
                principal.tenantId(), new CustomerId(customerId), MobilePhone.of(request.mobilePhone()));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/links")
    @Operation(summary = "Lista os tenants atualmente vinculados ao próprio titular")
    LinkMembershipResponse memberships(@AuthenticationPrincipal Jwt jwt) {
        var principal = principalConverter.convert(jwt);
        var customerId = requireLinkedCustomer(principal);
        var tenantIds = directory.findLinkedTenantMemberships(principal.tenantId(), customerId).stream()
                .map(tenant -> tenant.value())
                .sorted()
                .toList();
        logSelfRead(principal, customerId, "links");
        return new LinkMembershipResponse(tenantIds);
    }

    @PostMapping("/link/otp")
    @Operation(summary = "Envia OTP para o e-mail verificado associado a um cadastro existente")
    ResponseEntity<LinkChallengeResponse> requestLinkOtp(@AuthenticationPrincipal Jwt jwt) {
        var principal = principalConverter.convert(jwt);
        var challengeId = links.requestLink(principal, jwt.getClaimAsString("email"), emailVerified(jwt));
        return ResponseEntity.accepted().body(new LinkChallengeResponse(challengeId, 600));
    }

    @PostMapping("/link")
    @Operation(summary = "Confirma o OTP e vincula a identidade autenticada ao cadastro existente")
    ResponseEntity<Void> confirmLink(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConfirmLinkRequest request) {
        var principal = principalConverter.convert(jwt);
        links.confirmLink(
                principal, jwt.getClaimAsString("email"), emailVerified(jwt), request.challengeId(), request.code());
        return ResponseEntity.noContent().build();
    }

    private UUID requireLinkedCustomer(SubjectPrincipal principal) {
        var customerId = links.requireBoundCustomer(principal);
        directory
                .findLinkedCustomer(principal.tenantId(), customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado"));
        return customerId;
    }

    private static boolean emailVerified(Jwt jwt) {
        return Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
    }

    private static void logSelfRead(SubjectPrincipal principal, UUID customerId, String resource) {
        log.atInfo()
                .addKeyValue("audit.action", "subject.self-read")
                .addKeyValue("audit.resource", resource)
                .addKeyValue("customer.id", customerId)
                .addKeyValue("tenant.id", principal.tenantId())
                .log("Titular consultou os próprios dados");
    }

    record ConfirmLinkRequest(
            @NotBlank @Size(max = 36) String challengeId,
            @NotBlank @Size(min = 6, max = 6) String code) {}

    record LinkChallengeResponse(String challengeId, long expiresInSeconds) {}

    record LinkMembershipResponse(List<String> tenantIds) {}
}
