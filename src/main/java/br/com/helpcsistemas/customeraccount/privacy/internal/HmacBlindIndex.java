package br.com.helpcsistemas.customeraccount.privacy.internal;

import br.com.helpcsistemas.customeraccount.privacy.BlindIndex;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 com chave secreta; saída base64url sem padding (segura para nomes de stream e índices). */
class HmacBlindIndex implements BlindIndex {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    HmacBlindIndex(String base64Key) {
        var raw = Base64.getDecoder().decode(base64Key);
        if (raw.length < 32) {
            throw new IllegalStateException("app.privacy.blind-index-key deve ter ao menos 32 bytes");
        }
        this.key = new SecretKeySpec(raw, ALGORITHM);
    }

    @Override
    public String compute(String purpose, String normalizedValue) {
        try {
            // Mac não é thread-safe; instância por chamada (barato) em vez de ThreadLocal (ruim com virtual threads).
            var mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            var digest = mac.doFinal((purpose + ":" + normalizedValue).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao calcular blind index", ex);
        }
    }
}
