package br.com.helpcsistemas.customeraccount.platform.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param tenantAdminAcrValues valores de {@code acr} aceitos para administração do tenant (ex.: {@code 2} = login com
 *     segundo fator no Keycloak). Vazio = não exige (somente desenvolvimento).
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(List<String> tenantAdminAcrValues) {

    public SecurityProperties {
        tenantAdminAcrValues = tenantAdminAcrValues == null ? List.of() : List.copyOf(tenantAdminAcrValues);
    }
}
