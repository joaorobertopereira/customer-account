package br.com.helpcsistemas.customeraccount.secrets;

import java.util.Map;
import java.util.Optional;

public interface SecretStore {

    /** @param path relativo à base da aplicação, ex.: {@code providers/twilio-verify/default} */
    Optional<Map<String, String>> read(String path);
}
