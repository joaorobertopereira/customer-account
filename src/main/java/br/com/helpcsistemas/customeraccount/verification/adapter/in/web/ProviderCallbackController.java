package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import br.com.helpcsistemas.customeraccount.shared.domain.ResourceNotFoundException;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedProviderCallbackPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSessionRegistryPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderCallbackInboxPort;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.VerificationProcessRepositoryPort;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Receives callbacks without a JWT; the configured provider HMAC is the authentication mechanism. */
@RestController
@RequestMapping("/api/v1/provider-callbacks")
class ProviderCallbackController {

    private static final Logger log = LoggerFactory.getLogger(ProviderCallbackController.class);
    static final int MAX_BODY_BYTES = 256 * 1024;
    private static final String UNKNOWN_TENANT = "unmatched-provider-session";

    private final List<HostedProviderCallbackPort> providers;
    private final HostedSessionRegistryPort sessions;
    private final VerificationProcessRepositoryPort processes;
    private final ProviderCallbackInboxPort inbox;
    private final Clock clock;

    ProviderCallbackController(
            List<HostedProviderCallbackPort> providers,
            HostedSessionRegistryPort sessions,
            VerificationProcessRepositoryPort processes,
            ProviderCallbackInboxPort inbox,
            Clock clock) {
        this.providers = providers;
        this.sessions = sessions;
        this.processes = processes;
        this.inbox = inbox;
        this.clock = clock;
    }

    @PostMapping(value = "/{providerId}", consumes = "application/json")
    ResponseEntity<Void> receive(
            @PathVariable String providerId, @RequestHeader HttpHeaders headers, @RequestBody byte[] body) {
        if (body.length > MAX_BODY_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        var id = ProviderId.of(providerId);
        var provider = providers.stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("PROVIDER_NOT_FOUND", "Provedor não encontrado"));

        HostedResult normalized;
        try {
            normalized = provider.parseCallback(body);
        } catch (RuntimeException ex) {
            log.atWarn()
                    .addKeyValue("security.event", "provider-callback-rejected")
                    .addKeyValue("provider.id", id)
                    .log("Payload do callback inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var session = sessions.findByProviderSession(id, normalized.sessionId());
        var tenantId = session.flatMap(reference -> processes
                        .findById(reference.verificationId())
                        .map(p -> p.tenantId().value()))
                .orElse(UNKNOWN_TENANT);
        if (!provider.authenticateCallback(headers.toSingleValueMap(), body, tenantId)) {
            log.atWarn()
                    .addKeyValue("security.event", "provider-callback-rejected")
                    .addKeyValue("provider.id", id)
                    .log("Assinatura ou timestamp do callback inválido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var eventId = normalized.providerEventId() == null
                        || normalized.providerEventId().isBlank()
                        || normalized.providerEventId().length() > 200
                ? digest(body)
                : normalized.providerEventId();
        inbox.enqueue(
                id,
                eventId,
                session.map(HostedSessionRegistryPort.HostedSessionReference::customerId)
                        .orElse(null),
                body,
                clock.instant());
        return ResponseEntity.accepted().build();
    }

    private static String digest(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
