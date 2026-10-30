package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fluxo ponta a ponta com infraestrutura real (KurrentDB + MongoDB via Testcontainers): comando → event store →
 * subscription → read model → consulta, incluindo isolamento de tenant e eliminação por crypto-shredding.
 */
@Testcontainers(disabledWithoutDocker = true)
class CustomerFlowIntegrationTests extends IntegrationTestSupport {

    private static final String CPF = "52998224725";

    @Test
    void fullCustomerLifecycle() throws Exception {
        var id = register(tenant("tenant-a", "customer:write"), CPF);

        // CQRS: leitura eventual pelo read model projetado.
        await().atMost(EVENTUAL)
                .untilAsserted(
                        () -> mvc.perform(get("/api/v1/customers/{id}", id).with(tenant("tenant-a", "customer:read")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.cpf").value(CPF))
                                .andExpect(jsonPath("$.email.value").value("maria.souza@example.com"))
                                .andExpect(jsonPath("$.mobilePhone.value").value("+5511912345678"))
                                .andExpect(jsonPath("$.version").value(1)));

        mvc.perform(post("/api/v1/customers/search")
                        .with(tenant("tenant-a", "customer:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"529.982.247-25\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // Isolamento: outro tenant não enxerga nem consegue alterar.
        mvc.perform(get("/api/v1/customers/{id}", id).with(tenant("tenant-b", "customer:read")))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/customers/{id}/email", id)
                        .with(tenant("tenant-b", "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invasor@example.com\"}"))
                .andExpect(status().isNotFound());

        // Unicidade global de CPF.
        mvc.perform(post("/api/v1/customers")
                        .with(tenant("tenant-b", "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_ALREADY_REGISTERED"));

        mvc.perform(put("/api/v1/customers/{id}/email", id)
                        .with(tenant("tenant-a", "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria.nova@example.com\"}"))
                .andExpect(status().isAccepted());
        await().atMost(EVENTUAL)
                .untilAsserted(
                        () -> mvc.perform(get("/api/v1/customers/{id}", id).with(tenant("tenant-a", "customer:read")))
                                .andExpect(jsonPath("$.email.value").value("maria.nova@example.com"))
                                .andExpect(jsonPath("$.email.verified").value(false))
                                .andExpect(jsonPath("$.version").value(2)));

        assertNoPlaintextPersonalDataAtRest(id);

        // Eliminação (LGPD): read model removido, chave destruída e CPF liberado para novo cadastro.
        mvc.perform(delete("/api/v1/customers/{id}", id).with(tenant("tenant-a", "customer:erase")))
                .andExpect(status().isAccepted());
        await().atMost(EVENTUAL)
                .untilAsserted(
                        () -> mvc.perform(get("/api/v1/customers/{id}", id).with(tenant("tenant-a", "customer:read")))
                                .andExpect(status().isNotFound()));
        await().atMost(EVENTUAL)
                .untilAsserted(() -> assertThat(mongo.findById(id, Document.class, "privacy_subject_keys"))
                        .isNull());
        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(post("/api/v1/customers")
                                .with(tenant("tenant-a", "customer:write"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registrationBody()))
                        .andExpect(status().isCreated()));
    }

    @Test
    void enforcesAuthenticationScopesAndTenantClaim() throws Exception {
        mvc.perform(get("/api/v1/customers/{id}", "0190f3b4-0000-7000-8000-000000000000"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/customers")
                        .with(tenant("tenant-a", "customer:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody()))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/customers/{id}", "0190f3b4-0000-7000-8000-000000000000")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_customer:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsProblemDetailsWithoutEchoingPersonalData() throws Exception {
        mvc.perform(post("/api/v1/customers")
                        .with(tenant("tenant-a", "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody().replace(CPF, "12345678900")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CPF"))
                .andExpect(jsonPath("$.detail").value("CPF inválido"));

        mvc.perform(post("/api/v1/customers")
                        .with(tenant("tenant-a", "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody().replace("1990-05-20", "2015-01-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CUSTOMER_UNDERAGE"));

        mvc.perform(post("/api/v1/customers")
                        .with(tenant("tenant-a", "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    private void assertNoPlaintextPersonalDataAtRest(String id) {
        var document = mongo.findById(id, Document.class, "customer_views");
        assertThat(document).isNotNull();
        assertThat(document.toJson()).doesNotContain(CPF).doesNotContain("maria.nova@example.com");

        var events = eventStore.readStream("customer-" + id);
        assertThat(events).hasSize(3);
        events.forEach(e -> assertThat(new String(e.data(), StandardCharsets.ISO_8859_1))
                .doesNotContain(CPF)
                .doesNotContain("maria"));
    }

    private static String registrationBody() {
        return registrationBody(CPF);
    }
}
