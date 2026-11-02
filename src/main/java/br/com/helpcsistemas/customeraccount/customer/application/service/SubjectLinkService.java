package br.com.helpcsistemas.customeraccount.customer.application.service;

import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectBindingRepositoryPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectIdentityAdminPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkChallengePort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkChallengePort.Challenge;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkCodeHasherPort;
import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectLinkOtpDeliveryPort;
import br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.security.SubjectPrincipal;
import io.micrometer.observation.annotation.Observed;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SubjectLinkService {

    static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_WINDOW = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(SubjectLinkService.class);

    private final CustomerDirectory customers;
    private final SubjectLinkChallengePort challenges;
    private final SubjectBindingRepositoryPort bindings;
    private final SubjectIdentityAdminPort identityAdmin;
    private final SubjectLinkOtpDeliveryPort delivery;
    private final SubjectLinkCodeHasherPort codeHasher;
    private final Clock clock;

    public SubjectLinkService(
            CustomerDirectory customers,
            SubjectLinkChallengePort challenges,
            SubjectBindingRepositoryPort bindings,
            SubjectIdentityAdminPort identityAdmin,
            SubjectLinkOtpDeliveryPort delivery,
            SubjectLinkCodeHasherPort codeHasher,
            Clock clock) {
        this.customers = customers;
        this.challenges = challenges;
        this.bindings = bindings;
        this.identityAdmin = identityAdmin;
        this.delivery = delivery;
        this.codeHasher = codeHasher;
        this.clock = clock;
    }

    @Observed(name = "subject.link", contextualName = "request-link-otp")
    public String requestLink(SubjectPrincipal principal, String email, boolean emailVerified) {
        requireUnlinkedToken(principal);
        if (!emailVerified || email == null || email.isBlank()) {
            throw new BusinessRuleException("VERIFIED_EMAIL_REQUIRED", "É necessário autenticar com e-mail verificado");
        }
        var customer = customers
                .findLinkedCustomerByEmail(principal.tenantId(), email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Nenhum cadastro elegível foi encontrado"));
        ensureNotLinkedToAnotherCustomer(principal.subject(), customer.id());
        var now = clock.instant();
        var existing = challenges.findLatestForSubject(principal.subject(), principal.tenantId(), now);
        if (existing.isPresent()
                && existing.get().createdAt().plus(RESEND_WINDOW).isAfter(now)) {
            throw new BusinessRuleException("LINK_OTP_RATE_LIMITED", "Aguarde antes de solicitar outro código");
        }
        var challengeId = UUID.randomUUID().toString();
        var code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        var hash = codeHasher.hash(
                challengeId, principal.subject(), principal.tenantId().value(), customer.id(), code);
        var challenge = new Challenge(
                challengeId, principal.subject(), principal.tenantId(), customer.id(), hash, now, now.plus(OTP_TTL), 0);
        challenges.save(challenge);
        try {
            delivery.send(customer.email(), code, OTP_TTL);
        } catch (RuntimeException ex) {
            challenges.delete(challengeId);
            throw ex;
        }
        return challengeId;
    }

    @Observed(name = "subject.link", contextualName = "confirm-link-otp")
    public void confirmLink(
            SubjectPrincipal principal, String email, boolean emailVerified, String challengeId, String code) {
        requireUnlinkedToken(principal);
        if (!emailVerified || email == null || email.isBlank()) {
            throw new BusinessRuleException("VERIFIED_EMAIL_REQUIRED", "É necessário autenticar com e-mail verificado");
        }
        var now = clock.instant();
        var challenge = challenges
                .find(challengeId, principal.subject(), principal.tenantId(), now)
                .orElseThrow(() -> new BusinessRuleException("LINK_OTP_INVALID", "Código inválido ou expirado"));
        var customer = customers
                .findLinkedCustomerByEmail(principal.tenantId(), email)
                .filter(c -> c.id().equals(challenge.customerId()))
                .orElseThrow(() ->
                        new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Nenhum cadastro elegível foi encontrado"));
        var expectedHash = codeHasher.hash(
                challenge.id(),
                principal.subject(),
                principal.tenantId().value(),
                customer.id(),
                code == null ? "" : code);
        var consumed = challenges.consume(challenge.id(), principal.subject(), principal.tenantId(), expectedHash, now);
        if (consumed.isEmpty()) {
            challenges.recordFailedAttempt(challenge.id(), principal.subject(), principal.tenantId(), now);
            throw new BusinessRuleException("LINK_OTP_INVALID", "Código inválido ou expirado");
        }
        ensureNotLinkedToAnotherCustomer(principal.subject(), customer.id());
        if (!bindings.bindIfUnboundOrSame(principal.subject(), customer.id())) {
            throw new BusinessRuleException("SUBJECT_ALREADY_LINKED", "Esta identidade já está vinculada");
        }
        identityAdmin.setCustomerId(principal.subject(), customer.email(), customer.id());
        log.atInfo()
                .addKeyValue("audit.action", "subject-login-linked")
                .addKeyValue("customer.id", customer.id())
                .addKeyValue("tenant.id", principal.tenantId())
                .log("Identidade do titular vinculada");
    }

    public UUID requireBoundCustomer(SubjectPrincipal principal) {
        if (principal.customerId() == null
                || bindings.findCustomerId(principal.subject())
                        .filter(principal.customerId()::equals)
                        .isEmpty()) {
            throw new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cadastro do titular não encontrado");
        }
        return principal.customerId();
    }

    private void ensureNotLinkedToAnotherCustomer(String subject, UUID customerId) {
        if (bindings.findCustomerId(subject)
                .filter(existing -> !existing.equals(customerId))
                .isPresent()) {
            throw new BusinessRuleException("SUBJECT_ALREADY_LINKED", "Esta identidade já está vinculada");
        }
    }

    private static void requireUnlinkedToken(SubjectPrincipal principal) {
        if (principal.customerId() != null) {
            throw new BusinessRuleException("SUBJECT_ALREADY_LINKED", "Esta identidade já está vinculada");
        }
    }
}
