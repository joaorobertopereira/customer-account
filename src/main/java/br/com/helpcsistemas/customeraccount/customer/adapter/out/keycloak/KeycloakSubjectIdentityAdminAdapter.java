package br.com.helpcsistemas.customeraccount.customer.adapter.out.keycloak;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import br.com.helpcsistemas.customeraccount.customer.application.port.out.SubjectIdentityAdminPort;
import br.com.helpcsistemas.customeraccount.customer.domain.model.Email;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Admin API restrita ao usuário do próprio JWT; nunca pesquisa identidade por e-mail. */
@Component
class KeycloakSubjectIdentityAdminAdapter implements SubjectIdentityAdminPort {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final KeycloakAdminProperties properties;
    private volatile String accessToken;
    private volatile Instant refreshAfter = Instant.EPOCH;

    KeycloakSubjectIdentityAdminAdapter(RestClient.Builder builder, KeycloakAdminProperties properties) {
        this.rest = builder.build();
        this.properties = properties;
    }

    @Override
    public void setCustomerId(String subject, String verifiedEmail, UUID customerId) {
        var token = serviceAccountToken();
        var basePath = "/admin/realms/" + properties.realm() + "/users/" + subject;
        var user = rest.get()
                .uri(properties.baseUrl() + basePath)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(MAP_TYPE);
        if (user == null
                || !Boolean.TRUE.equals(user.get("emailVerified"))
                || !sameEmail(user.get("email"), verifiedEmail)) {
            throw new ResponseStatusException(CONFLICT, "A identidade não possui o e-mail verificado informado");
        }
        var attributes = mutableMap(user.get("attributes"));
        var current = attributeValue(attributes.get("customer_id"));
        if (current != null && !current.equals(customerId.toString())) {
            throw new ResponseStatusException(CONFLICT, "A identidade já está vinculada a outro cadastro");
        }
        if (customerId.toString().equals(current)) {
            return;
        }
        attributes.put("customer_id", List.of(customerId.toString()));
        user.put("attributes", attributes);
        rest.put()
                .uri(properties.baseUrl() + basePath)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity();
    }

    private String serviceAccountToken() {
        synchronized (this) {
            if (accessToken != null && Instant.now().isBefore(refreshAfter)) {
                return accessToken;
            }
            if (properties.clientSecret() == null || properties.clientSecret().isBlank()) {
                throw new ResponseStatusException(
                        SERVICE_UNAVAILABLE, "Credenciais do Keycloak Admin não configuradas");
            }
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", properties.clientId());
            form.add("client_secret", properties.clientSecret());
            var response = rest.post()
                    .uri(properties.baseUrl() + "/realms/" + properties.realm() + "/protocol/openid-connect/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MAP_TYPE);
            if (response == null || !(response.get("access_token") instanceof String token)) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Não foi possível autenticar no Keycloak Admin");
            }
            var expiresIn = response.get("expires_in") instanceof Number seconds ? seconds.longValue() : 60L;
            accessToken = token;
            refreshAfter = Instant.now().plusSeconds(Math.max(1, expiresIn - 15));
            return token;
        }
    }

    private static boolean sameEmail(Object current, String expected) {
        if (!(current instanceof String email)) {
            return false;
        }
        try {
            return Email.of(email).equals(Email.of(expected));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static Map<String, Object> mutableMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, value) -> {
            if (key instanceof String stringKey) {
                result.put(stringKey, value);
            }
        });
        return result;
    }

    private static String attributeValue(Object raw) {
        if (raw instanceof List<?> values && !values.isEmpty() && values.getFirst() instanceof String value) {
            return value;
        }
        if (raw instanceof String value) {
            return value;
        }
        return null;
    }
}
