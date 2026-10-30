package br.com.helpcsistemas.customeraccount.tenant.adapter.out.http;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.tenant.application.port.out.WebhookTargetGuardPort;
import br.com.helpcsistemas.customeraccount.tenant.config.TenantWebhookProperties;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Bloqueia destinos internos (loopback, redes privadas, link-local/metadados de nuvem, CGNAT, multicast) e http.
 * Checado ao configurar e antes de cada envio. Limitação conhecida: DNS rebinding entre a checagem e a conexão —
 * em produção, use também um proxy de saída (egress) com allowlist.
 */
@Component
class DnsWebhookTargetGuard implements WebhookTargetGuardPort {

    private final TenantWebhookProperties properties;

    DnsWebhookTargetGuard(TenantWebhookProperties properties) {
        this.properties = properties;
    }

    @Override
    public void ensureAllowed(URI url) {
        var scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !(scheme.equals("http") && properties.allowInsecureTargets())) {
            throw notAllowed("O webhook deve usar https");
        }
        if (properties.allowPrivateTargets()) {
            return;
        }
        try {
            for (var address : InetAddress.getAllByName(url.getHost())) {
                if (isInternal(address)) {
                    throw notAllowed("O webhook não pode apontar para endereços internos");
                }
            }
        } catch (UnknownHostException ex) {
            throw notAllowed("Host do webhook não encontrado");
        }
    }

    static boolean isInternal(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        var bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            // 100.64.0.0/10 (CGNAT)
            return (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
        }
        // fc00::/7 (unique local)
        return address instanceof Inet6Address && (bytes[0] & 0xFE) == 0xFC;
    }

    private static InvalidValueException notAllowed(String message) {
        return new InvalidValueException("WEBHOOK_TARGET_NOT_ALLOWED", message);
    }
}
