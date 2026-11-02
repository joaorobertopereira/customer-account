package br.com.helpcsistemas.customeraccount.customer.adapter.out.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.keycloak-admin")
public record KeycloakAdminProperties(String baseUrl, String realm, String clientId, String clientSecret) {}
