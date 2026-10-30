package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import br.com.helpcsistemas.customeraccount.shared.domain.Actor;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.ConfigureRuleRequest;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.IncidentResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.PolicyChangeResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.PolicyResponse;
import br.com.helpcsistemas.customeraccount.verification.adapter.in.web.VerificationDtos.ProviderResponse;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.ConfigureVerificationPolicyUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.FindVerificationUseCase;
import br.com.helpcsistemas.customeraccount.verification.application.port.in.QueryVerificationPolicyUseCase;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Administração do tenant (role {@code tenant-admin}): escolha de provedores e acompanhamento de falhas. */
@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Administração do tenant - validações")
class TenantVerificationAdminController {

    private final ConfigureVerificationPolicyUseCase configure;
    private final QueryVerificationPolicyUseCase policies;
    private final FindVerificationUseCase verifications;

    TenantVerificationAdminController(
            ConfigureVerificationPolicyUseCase configure,
            QueryVerificationPolicyUseCase policies,
            FindVerificationUseCase verifications) {
        this.configure = configure;
        this.policies = policies;
        this.verifications = verifications;
    }

    @GetMapping("/verification-policy")
    @Operation(summary = "Política vigente (provedor por tipo de validação)")
    PolicyResponse current(@Parameter(hidden = true) TenantId tenant) {
        return VerificationWebMapper.toResponse(policies.current(tenant));
    }

    @PutMapping("/verification-policy/rules/{type}")
    @Operation(summary = "Define/altera o provedor de um tipo de validação; vale a partir da próxima validação")
    PolicyResponse configure(
            @Parameter(hidden = true) TenantId tenant,
            @Parameter(hidden = true) Actor actor,
            @PathVariable VerificationType type,
            @Valid @RequestBody ConfigureRuleRequest request) {
        var rule = VerificationWebMapper.toRule(type, request);
        return VerificationWebMapper.toResponse(configure.configure(tenant, rule, actor.id(), request.reason()));
    }

    @GetMapping("/verification-policy/history")
    @Operation(summary = "Histórico auditável de alterações da política")
    List<PolicyChangeResponse> history(@Parameter(hidden = true) TenantId tenant) {
        return policies.history(tenant).stream()
                .map(VerificationWebMapper::toResponse)
                .toList();
    }

    @GetMapping("/verification-providers")
    @Operation(summary = "Provedores disponíveis na plataforma e tipos suportados")
    List<ProviderResponse> providers() {
        return policies.availableProviders().stream()
                .map(VerificationWebMapper::toResponse)
                .toList();
    }

    @GetMapping("/verification-incidents")
    @Operation(summary = "Falhas de provedores registradas para o tenant (mais recentes primeiro)")
    List<IncidentResponse> incidents(
            @Parameter(hidden = true) TenantId tenant, @RequestParam(defaultValue = "50") int limit) {
        return verifications.recentIncidents(tenant, limit).stream()
                .map(VerificationWebMapper::toResponse)
                .toList();
    }
}
