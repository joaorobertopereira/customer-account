package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import br.com.helpcsistemas.customeraccount.secrets.SecretStore;
import br.com.helpcsistemas.customeraccount.shared.domain.TenantId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Cada provedor tem sua própria credencial no cofre ({@code providers/<provedor>/default}). Um tenant com contrato
 * próprio pode ter credencial específica ({@code providers/<provedor>/tenants/<tenant>}), que tem precedência.
 */
@Component
public class ProviderCredentialsResolver {

    private final SecretStore secrets;

    ProviderCredentialsResolver(SecretStore secrets) {
        this.secrets = secrets;
    }

    public ProviderCredentials resolve(ProviderId provider, TenantId tenant) {
        var base = "providers/" + provider.value();
        var values = secrets.read(base + "/tenants/" + tenant.value())
                .or(() -> secrets.read(base + "/default"))
                .orElseThrow(() -> new ProviderCredentialsMissingException(provider, "nenhuma credencial cadastrada"));
        return new ProviderCredentials(provider, values);
    }

    /** {@link #toString()} nunca expõe valores. */
    public record ProviderCredentials(ProviderId provider, Map<String, String> values) {

        public String require(String key) {
            var value = values.get(key);
            if (value == null || value.isBlank()) {
                throw new ProviderCredentialsMissingException(provider, "campo " + key);
            }
            return value;
        }

        @Override
        public String toString() {
            return "ProviderCredentials[" + provider + "]";
        }
    }
}
