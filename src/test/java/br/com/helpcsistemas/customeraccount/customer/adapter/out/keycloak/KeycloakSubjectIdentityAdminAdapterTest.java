package br.com.helpcsistemas.customeraccount.customer.adapter.out.keycloak;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class KeycloakSubjectIdentityAdminAdapterTest {

    private static final String BASE = "http://keycloak.test";
    private static final String USER_URL = BASE + "/admin/realms/customer-account/users/subject-1";
    private static final String USER_WITHOUT_ATTRIBUTES =
            "{\"id\":\"subject-1\",\"email\":\"maria@example.com\",\"emailVerified\":true}";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server =
            MockRestServiceServer.bindTo(builder).build();
    private final KeycloakSubjectIdentityAdminAdapter adapter = new KeycloakSubjectIdentityAdminAdapter(
            builder, new KeycloakAdminProperties(BASE, "customer-account", "customer-account-admin", "secret"));

    @Test
    void confirmsThatTheAttributeWasPersisted() {
        var customerId = UUID.randomUUID();
        expectTokenGetAndPut();
        server.expect(requestTo(USER_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"subject-1\",\"attributes\":{\"customer_id\":[\"" + customerId + "\"]}}",
                        MediaType.APPLICATION_JSON));

        assertThatCode(() -> adapter.setCustomerId("subject-1", "maria@example.com", customerId))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void failsWhenKeycloakSilentlyDropsTheAttribute() {
        // Keycloak 24+ sem o atributo no user profile: PUT 2xx, mas customer_id some.
        expectTokenGetAndPut();
        server.expect(requestTo(USER_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(USER_WITHOUT_ATTRIBUTES, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.setCustomerId("subject-1", "maria@example.com", UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> org.assertj.core.api.Assertions.assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    private void expectTokenGetAndPut() {
        server.expect(requestTo(BASE + "/realms/customer-account/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"t\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(USER_WITHOUT_ATTRIBUTES, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL)).andExpect(method(HttpMethod.PUT)).andRespond(withNoContent());
    }
}
