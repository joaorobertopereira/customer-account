package br.com.helpcsistemas.customeraccount.tenant.application.port.out;

import java.net.URI;

/** Proteção contra SSRF: o webhook não pode apontar para a rede interna nem usar transporte inseguro. */
public interface WebhookTargetGuardPort {

    /** @throws br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException se o destino for proibido */
    void ensureAllowed(URI url);
}
