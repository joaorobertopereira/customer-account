package br.com.helpcsistemas.customeraccount;

import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class SubjectSecurityIntegrationTests extends IntegrationTestSupport {

    @Test
    void granularReadScopesOnlyExposeTheirFieldGroupsOnGetAndSearch() throws Exception {
        var id = register(tenant("tenant-a", "customer:write"), "56398274164");

        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(
                                get("/api/v1/customers/{id}", id).with(tenant("tenant-a", "customer:read:identity")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.cpf").value("56398274164"))
                        .andExpect(jsonPath("$.personalData.fullName").value("Maria da Silva Souza"))
                        .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.nullValue()))
                        .andExpect(jsonPath("$.address").value(org.hamcrest.Matchers.nullValue())));

        mvc.perform(post("/api/v1/customers/search")
                        .with(tenant("tenant-a", "customer:read:contact"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"56398274164\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.email.value").value("maria.souza@example.com"))
                .andExpect(jsonPath("$.mobilePhone.value").value("+5511912345678"))
                .andExpect(jsonPath("$.personalData").value(org.hamcrest.Matchers.nullValue()));

        mvc.perform(get("/api/v1/customers/{id}", id).with(tenant("tenant-a", "customer:read:address")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.city").value("São Paulo"))
                .andExpect(jsonPath("$.cpf").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void meRoutesRequireBothMeScopeAndWellFormedCustomerClaim() throws Exception {
        mvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "tenant-a"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_me:read"))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(token ->
                                        token.claim("tenant_id", "tenant-a").claim("customer_id", "not-a-uuid"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_me:read"))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/me")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "tenant-a")
                                        .claim("customer_id", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("SCOPE_customer:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void linkedSubjectReadsOnlyItsDirectoryEntryAndCannotCrossTenant() throws Exception {
        var id = register(tenant("tenant-a", "customer:write"), "82736491564");
        await().atMost(EVENTUAL)
                .untilAsserted(
                        () -> mvc.perform(get("/api/v1/customers/{id}", id).with(tenant("tenant-a", "customer:read")))
                                .andExpect(status().isOk()));

        var subject = "subject-" + UUID.randomUUID();
        mongo.insert(new Document("_id", subject).append("customerId", id), "subject_customer_bindings");
        var token = subjectToken(subject, id, "tenant-a");

        mvc.perform(get("/api/v1/me").with(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email.value").value("maria.souza@example.com"));

        mvc.perform(get("/api/v1/me").with(subjectToken(subject, id, "tenant-b")))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/me").with(subjectToken("unbound-" + UUID.randomUUID(), id, "tenant-a")))
                .andExpect(status().isNotFound());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor subjectToken(
            String subject, String customerId, String tenantId) {
        return jwt().jwt(token -> token.subject(subject)
                        .claim("tenant_id", tenantId)
                        .claim("customer_id", customerId)
                        .claim("scope", "me:read"))
                .authorities(new SimpleGrantedAuthority("SCOPE_me:read"));
    }
}
