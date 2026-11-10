package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class ConsentFlowIntegrationTests extends IntegrationTestSupport {
    private static final String ORIGIN = "phase4-origin";
    private static final String NEW_TENANT = "phase4-new";
    private static final String CPF = "98765432100";

    @Test
    void pendingApprovalMinimizationRevocationAndUnlinkNeverLeakData() throws Exception {
        var id = register(tenant(ORIGIN, "customer:write"), CPF);
        var holder = holder(ORIGIN, id);
        putPurpose(ORIGIN, "identity", "CONTRACT", "[\"cpf\",\"personalData.fullName\"]");
        putPurpose(NEW_TENANT, "contact", "CONSENT", "[\"email\"]");

        await().atMost(EVENTUAL)
                .untilAsserted(
                        () -> mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(ORIGIN, "customer:read")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.cpf").value(CPF))
                                .andExpect(jsonPath("$.email").doesNotExist())
                                .andExpect(jsonPath("$.address").doesNotExist())
                                .andExpect(jsonPath("$.personalData.fullName").exists())
                                .andExpect(jsonPath("$.personalData.birthDate").doesNotExist()));

        mvc.perform(post("/api/v1/customers")
                        .with(tenant(NEW_TENANT, "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(CPF).replace("maria.souza@example.com", "suggested@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content()
                        .json("{\"status\":\"LINK_PENDING\"}", org.springframework.test.json.JsonCompareMode.STRICT));
        mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(NEW_TENANT, "customer:read")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/customers/search")
                        .with(tenant(NEW_TENANT, "customer:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/customers/{id}/consents", id)
                        .with(tenant(NEW_TENANT, "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grant()))
                .andExpect(status().isNotFound());

        var pending = mvc.perform(get("/api/v1/me/links/requests").with(holder))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String requestId = JsonPath.read(pending, "$[0].requestId");
        mvc.perform(post("/api/v1/me/links/requests/{id}/approve", requestId).with(holder(NEW_TENANT, id)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/me/links/requests/{id}/approve", requestId).with(holder))
                .andExpect(status().isAccepted());
        await().atMost(EVENTUAL)
                .untilAsserted(
                        () -> mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(NEW_TENANT, "customer:read")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.cpf").doesNotExist())
                                .andExpect(jsonPath("$.email").doesNotExist())
                                .andExpect(jsonPath("$.personalData").doesNotExist()));
        mvc.perform(get("/api/v1/me/links/suggestions").with(holder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].suggestedEmail.value").value("suggested@example.com"));

        mvc.perform(post("/api/v1/me/consents/{tenant}", NEW_TENANT)
                        .with(holder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grant()))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(NEW_TENANT, "customer:read:contact")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email.value").value("maria.souza@example.com"))
                .andExpect(jsonPath("$.mobilePhone").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist());
        mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(NEW_TENANT, "customer:read:identity")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.cpf").doesNotExist());
        mvc.perform(post("/api/v1/customers/search")
                        .with(tenant(NEW_TENANT, "customer:read:contact"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + CPF + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.cpf").doesNotExist());

        mvc.perform(put("/api/v1/tenant/verification-policy/rules/FACE_MATCH")
                        .with(tenantAdmin(NEW_TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"primaryProvider":"simulated-approve","minimumScore":70,
                                "onInconclusive":"MANUAL_REVIEW","enabled":true,"reason":"Phase4 cleanup test"}
                                """))
                .andExpect(status().isOk());
        var verification = mvc.perform(post("/api/v1/verifications")
                        .with(tenant(NEW_TENANT, "verification:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","type":"FACE_MATCH"}
                                """.formatted(id)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String verificationId = JsonPath.read(verification, "$.id");
        await().atMost(EVENTUAL)
                .untilAsserted(() -> assertThat(mongo.findById(verificationId, Document.class, "verification_views"))
                        .isNotNull());

        await().atMost(EVENTUAL).untilAsserted(() -> {
            var view = mongo.findById(id + ":" + NEW_TENANT, Document.class, "consent_views");
            assertThat(view).isNotNull();
            assertThat(view.toJson()).doesNotContain("phase4-session").doesNotContain("a".repeat(64));
        });
        var consentEvents = eventStore.readStream("consent-" + id + "-" + NEW_TENANT);
        assertThat(consentEvents).hasSize(1);
        assertThat(new String(consentEvents.getFirst().data(), StandardCharsets.ISO_8859_1))
                .doesNotContain("phase4-session")
                .doesNotContain("a".repeat(64));
        mvc.perform(delete("/api/v1/me/consents/{tenant}/contact", NEW_TENANT)
                        .with(holder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Holder revoked\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(NEW_TENANT, "customer:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());

        mvc.perform(delete("/api/v1/customers/{id}/link", id).with(tenant(NEW_TENANT, "customer:erase")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/consents/{tenant}", NEW_TENANT)
                        .with(holder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(grant()))
                .andExpect(status().isAccepted());
        mvc.perform(delete("/api/v1/customers/{id}/link", id).with(tenant(NEW_TENANT, "customer:write")))
                .andExpect(status().isAccepted());
        // A checagem forte bloqueia antes mesmo de a projeção remover o vínculo.
        mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(NEW_TENANT, "customer:read")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/customers/{id}/consents", id).with(tenant(NEW_TENANT, "customer:read")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/links/requests").with(holder(NEW_TENANT, id)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(ORIGIN, "customer:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(CPF));
        await().atMost(EVENTUAL)
                .untilAsserted(() -> assertThat(mongo.findById(id + ":" + NEW_TENANT, Document.class, "consent_views"))
                        .isNull());
        await().atMost(EVENTUAL)
                .untilAsserted(() -> assertThat(mongo.findById(verificationId, Document.class, "verification_views"))
                        .isNull());
        mvc.perform(get("/api/v1/verifications/{id}", verificationId).with(tenant(NEW_TENANT, "verification:read")))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/customers")
                        .with(tenant(NEW_TENANT, "customer:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(CPF)))
                .andExpect(status().isAccepted());
        var next = mvc.perform(get("/api/v1/me/links/requests").with(holder))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String nextRequest = JsonPath.read(next, "$[0].requestId");
        mvc.perform(post("/api/v1/me/links/requests/{id}/approve", nextRequest).with(holder))
                .andExpect(status().isAccepted());
        await().atMost(EVENTUAL)
                .untilAsserted(
                        () -> mvc.perform(get("/api/v1/customers/{id}", id).with(tenant(NEW_TENANT, "customer:read")))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").doesNotExist()));
        mvc.perform(get("/api/v1/customers/{id}/consents", id).with(tenant(NEW_TENANT, "customer:read")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/verifications/{id}", verificationId).with(tenant(NEW_TENANT, "verification:read")))
                .andExpect(status().isNotFound());
    }

    @Test
    void purposeAdministrationRequiresTenantAdminAndValidFields() throws Exception {
        mvc.perform(
                        put("/api/v1/tenant/purposes/forbidden")
                                .with(tenant(ORIGIN, "customer:write"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"description\":\"x\",\"legalBasis\":\"CONTRACT\",\"policyVersion\":\"1\",\"fields\":[\"cpf\"]}"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/v1/tenant/purposes/invalid")
                                .with(tenantAdmin(ORIGIN))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"description\":\"x\",\"legalBasis\":\"CONTRACT\",\"policyVersion\":\"1\",\"fields\":[\"password\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PURPOSE"));
    }

    private void putPurpose(String tenant, String code, String legalBasis, String fields) throws Exception {
        mvc.perform(put("/api/v1/tenant/purposes/{code}", code)
                        .with(tenantAdmin(tenant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Phase4 purpose","legalBasis":"%s","policyVersion":"1","fields":%s}
                                """.formatted(legalBasis, fields)))
                .andExpect(status().isNoContent());
    }

    private static JwtRequestPostProcessor holder(String tenant, String id) {
        return jwt().jwt(token -> token.subject("phase4-holder")
                        .claim("tenant_id", tenant)
                        .claim("customer_id", id)
                        .claim("email", "maria.souza@example.com")
                        .claim("email_verified", true)
                        .claim("sid", "phase4-session")
                        .claim("azp", tenant + "-web")
                        .claim("acr", "2"))
                .authorities(new SimpleGrantedAuthority("SCOPE_me:read"), new SimpleGrantedAuthority("SCOPE_me:write"));
    }

    private static String grant() {
        return """
                {"purpose":"contact","policyVersion":"1","evidence":{"channel":"web","sessionId":"ignored",
                "textHash":"%s"}}
                """.formatted("a".repeat(64));
    }
}
