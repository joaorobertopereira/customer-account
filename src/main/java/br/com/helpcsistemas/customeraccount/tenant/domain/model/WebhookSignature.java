package br.com.helpcsistemas.customeraccount.tenant.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Assinatura do webhook: {@code v1=hex(HMAC-SHA256(secret, timestamp + "." + body))}. O tenant recalcula com o
 * segredo dele, compara em tempo constante e rejeita timestamps antigos (proteção contra replay).
 */
public final class WebhookSignature {

    public static final String VERSION = "v1";

    private WebhookSignature() {}

    public static String sign(String secret, long timestampSeconds, String body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var digest = mac.doFinal((timestampSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return VERSION + "=" + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao assinar webhook", ex);
        }
    }
}
