package br.com.helpcsistemas.customeraccount.secrets.internal;

import br.com.helpcsistemas.customeraccount.secrets.SecretStore;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Segredos vindos da configuração. Somente desenvolvimento e testes. */
class LocalSecretStore implements SecretStore {

    private static final Logger log = LoggerFactory.getLogger(LocalSecretStore.class);

    private final Map<String, Map<String, String>> secrets;

    LocalSecretStore(Map<String, Map<String, String>> secrets) {
        this.secrets = secrets;
        log.warn("Segredos LOCAIS em uso (app.secrets.provider=local). Proibido em produção.");
    }

    @Override
    public Optional<Map<String, String>> read(String path) {
        return Optional.ofNullable(secrets.get(path)).map(Map::copyOf);
    }
}
