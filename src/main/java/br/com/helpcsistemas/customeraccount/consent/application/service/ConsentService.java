package br.com.helpcsistemas.customeraccount.consent.application.service;

import br.com.helpcsistemas.customeraccount.consent.application.port.out.ConsentRepository;
import br.com.helpcsistemas.customeraccount.consent.application.port.out.PurposeRepository;
import br.com.helpcsistemas.customeraccount.consent.domain.event.ConsentEvent.ConsentGranted;
import br.com.helpcsistemas.customeraccount.consent.domain.model.ConsentEvidence;
import br.com.helpcsistemas.customeraccount.consent.domain.model.Purpose;
import br.com.helpcsistemas.customeraccount.customer.CustomerDirectory;
import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConsentService {
    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);
    private final ConsentRepository ledgers;
    private final PurposeRepository purposes;
    private final CustomerDirectory customers;
    private final Clock clock;

    public ConsentService(
            ConsentRepository ledgers, PurposeRepository purposes, CustomerDirectory customers, Clock clock) {
        this.ledgers = ledgers;
        this.purposes = purposes;
        this.customers = customers;
        this.clock = clock;
    }

    public void putPurpose(TenantId tenant, Purpose purpose) {
        var previous = purposes.find(tenant, purpose.code());
        if (previous.isPresent()
                && !previous.get().equals(purpose)
                && previous.get().policyVersion().equals(purpose.policyVersion())) {
            throw new br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException(
                    "POLICY_VERSION_REQUIRED", "Altere a versão ao modificar uma finalidade");
        }
        purposes.save(tenant, purpose);
        audit(tenant, null, "purpose.updated");
    }

    public List<Purpose> purposes(TenantId tenant) {
        return purposes.list(tenant);
    }

    public void grant(TenantId tenant, UUID customer, String code, String version, ConsentEvidence evidence) {
        var linkedAt = customers.linkedAt(tenant, customer).orElseThrow(ConsentService::notFound);
        var purpose = purposes.find(tenant, code)
                .orElseThrow(() -> new ResourceNotFoundException("PURPOSE_NOT_FOUND", "Finalidade não encontrada"));
        if (!purpose.policyVersion().equals(version)) {
            throw new br.com.helpcsistemas.customeraccount.shared.domain.BusinessRuleException(
                    "CONSENT_POLICY_CHANGED", "O consentimento deve corresponder à versão atual da política");
        }
        var ledger = ledgers.load(customer, tenant);
        ledger.grant(purpose, evidence, linkedAt, clock);
        ledgers.save(ledger);
        audit(tenant, customer, "consent.granted");
    }

    public void revoke(TenantId tenant, UUID customer, String purpose, String reason) {
        customers.findLinkedCustomer(tenant, customer).orElseThrow(ConsentService::notFound);
        var ledger = ledgers.load(customer, tenant);
        ledger.revoke(purpose, reason, clock);
        ledgers.save(ledger);
        audit(tenant, customer, "consent.revoked");
    }

    public List<ConsentGranted> list(TenantId tenant, UUID customer) {
        var linkedAt = customers.linkedAt(tenant, customer).orElseThrow(ConsentService::notFound);
        return ledgers.load(customer, tenant).granted().values().stream()
                .filter(e -> linkedAt.equals(e.linkedAt()))
                .toList();
    }

    public Set<String> allowedFields(TenantId tenant, UUID customer) {
        var linkedAt = customers.linkedAt(tenant, customer).orElseThrow(ConsentService::notFound);
        var granted = ledgers.load(customer, tenant).granted();
        return purposes.list(tenant).stream()
                .filter(p -> {
                    if (p.legalBasis() != Purpose.LegalBasis.CONSENT) {
                        return true;
                    }
                    var consent = granted.get(p.code());
                    return consent != null
                            && p.policyVersion().equals(consent.policyVersion())
                            && linkedAt.equals(consent.linkedAt());
                })
                .flatMap(p -> p.fields().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Cliente não encontrado");
    }

    private static void audit(TenantId tenant, UUID customer, String action) {
        log.atInfo()
                .addKeyValue("audit.action", action)
                .addKeyValue("tenant.id", tenant)
                .addKeyValue("customer.id", customer)
                .log("Política de acesso atualizada");
    }
}
