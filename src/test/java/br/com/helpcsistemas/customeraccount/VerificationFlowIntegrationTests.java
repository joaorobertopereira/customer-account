package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.hosted.SimulatedHostedProvider;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Validações com provedor escolhido pelo tenant: contingência automática com registro da falha, alteração da
 * política valendo na próxima chamada, histórico auditável, isolamento e eliminação.
 */
@Testcontainers(disabledWithoutDocker = true)
class VerificationFlowIntegrationTests extends IntegrationTestSupport {

    private static final String TENANT_A = "tenant-app-a";
    private static final String TENANT_B = "tenant-app-b";
    private static final String TENANT_HOSTED = "tenant-app-hosted";

    @Autowired
    private SimulatedHostedProvider simulatedHostedProvider;

    @Test
    void hostedIdentityReturnsReusableLinkAndProcessesSignedCallback() throws Exception {
        configure(TENANT_HOSTED, "IDENTITY", "simulated-hosted", null, "Fluxo hospedado")
                .andExpect(status().isOk());
        var customerId = register(tenant(TENANT_HOSTED, "customer:write"), "12345678909");

        var initial = verify(TENANT_HOSTED, customerId, "IDENTITY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_HOLDER"))
                .andExpect(jsonPath("$.link.url").isString())
                .andExpect(jsonPath("$.link.expiresAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String verificationId = JsonPath.read(initial, "$.id");
        String link = JsonPath.read(initial, "$.link.url");
        String sessionId = link.substring(link.lastIndexOf('/') + 1);

        verify(TENANT_HOSTED, customerId, "IDENTITY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(verificationId))
                .andExpect(jsonPath("$.reused").value(true));
        mvc.perform(get("/dev/hosted-verification/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("FRAUD_DETECTED")));

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String body = """
                {"eventId":"callback-%s","sessionId":"%s","status":"COMPLETED","score":94,"reasonCodes":[],"fraudSignals":[],"occurredAt":"%s"}
                """.formatted(UUID.randomUUID(), sessionId, Instant.now());
        mvc.perform(post("/api/v1/provider-callbacks/simulated-hosted")
                        .header("X-Timestamp", timestamp)
                        .header("X-Signature", "v1=invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
        var signature = simulatedHostedProvider.signature(timestamp, body);
        mvc.perform(post("/api/v1/provider-callbacks/simulated-hosted")
                        .header("X-Timestamp", timestamp)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/provider-callbacks/simulated-hosted")
                        .header("X-Timestamp", timestamp)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(get("/api/v1/verifications/{id}", verificationId)
                                .with(tenant(TENANT_HOSTED, "verification:read")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.outcome").value("APPROVED"))
                        .andExpect(jsonPath("$.validUntil").exists())
                        .andExpect(jsonPath("$.link").doesNotExist()));
        mvc.perform(get("/api/v1/customers/{id}/verification-status", customerId)
                        .queryParam("type", "IDENTITY")
                        .with(tenant(TENANT_HOSTED, "verification:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.verificationId").value(verificationId));
        assertThat(eventStore.readStream("verification-" + verificationId).stream()
                        .filter(event -> event.type().equals("ProviderResultReceived.v1"))
                        .count())
                .isEqualTo(1);

        var revalidation = verify(TENANT_HOSTED, customerId, "IDENTITY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reused").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secondId = JsonPath.read(revalidation, "$.id");
        String secondSession = ((String) JsonPath.read(revalidation, "$.link.url"))
                .substring(((String) JsonPath.read(revalidation, "$.link.url")).lastIndexOf('/') + 1);
        String fraudTimestamp = Long.toString(Instant.now().getEpochSecond());
        String fraudBody = """
                {"eventId":"callback-fraud-%s","sessionId":"%s","status":"FRAUD_DETECTED","fraudSignals":["SIMULATED_FRAUD"],"occurredAt":"%s"}
                """.formatted(UUID.randomUUID(), secondSession, Instant.now());
        mvc.perform(post("/api/v1/provider-callbacks/simulated-hosted")
                        .header("X-Timestamp", fraudTimestamp)
                        .header("X-Signature", simulatedHostedProvider.signature(fraudTimestamp, fraudBody))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fraudBody))
                .andExpect(status().isAccepted());
        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(get("/api/v1/verifications/{id}", secondId)
                                .with(tenant(TENANT_HOSTED, "verification:read")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.outcome").value("FRAUD_SUSPECTED")));
        mvc.perform(get("/api/v1/customers/{id}/verification-status", customerId)
                        .queryParam("type", "IDENTITY")
                        .with(tenant(TENANT_HOSTED, "verification:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_APPROVED"));

        mvc.perform(delete("/api/v1/customers/{id}", customerId).with(tenant(TENANT_HOSTED, "customer:erase")))
                .andExpect(status().isAccepted());
        await().atMost(EVENTUAL)
                .untilAsserted(() -> assertThat(mongo.count(
                                Query.query(Criteria.where("customerId").is(customerId.toString())),
                                "verification_session_index"))
                        .isZero());
    }

    @Test
    void tenantProviderChoiceWithAutomaticFailoverAndAuditedChanges() throws Exception {
        configure(TENANT_A, "FACE_MATCH", "simulated-unavailable", "simulated-approve", "Contrato inicial")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.rules[0].primaryProvider").value("simulated-unavailable"));
        var customerId = register(tenant(TENANT_A, "customer:write"), "11144477735");

        // Primário fora do ar: segue pela contingência e a falha fica registrada no resultado.
        var first = verify(TENANT_A, customerId, "FACE_MATCH")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.provider").value("simulated-approve"))
                .andExpect(jsonPath("$.policyVersion").value(0))
                .andExpect(jsonPath("$.failover.occurred").value(true))
                .andExpect(jsonPath("$.failover.failures[0].provider").value("simulated-unavailable"))
                .andExpect(jsonPath("$.failover.failures[0].errorCode").value("PROVIDER_ERROR"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstId = JsonPath.read(first, "$.id");

        // Alteração do administrador vale já na próxima validação.
        configure(TENANT_A, "FACE_MATCH", "simulated-reject", null, "Troca de fornecedor")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        verify(TENANT_A, customerId, "FACE_MATCH")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.provider").value("simulated-reject"))
                .andExpect(jsonPath("$.policyVersion").value(1))
                .andExpect(jsonPath("$.failover.occurred").value(false))
                .andExpect(jsonPath("$.reasonCodes", hasItem("FACE_MISMATCH")));

        mvc.perform(get("/api/v1/tenant/verification-policy/history").with(tenantAdmin(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].previous.primaryProvider").value("simulated-unavailable"))
                .andExpect(jsonPath("$[1].current.primaryProvider").value("simulated-reject"))
                .andExpect(jsonPath("$[1].changedBy").value("admin." + TENANT_A))
                .andExpect(jsonPath("$[1].reason").value("Troca de fornecedor"));

        // Read model (CQRS) e incidentes do tenant.
        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(
                                get("/api/v1/verifications/{id}", firstId).with(tenant(TENANT_A, "verification:read")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("COMPLETED"))
                        .andExpect(jsonPath("$.outcome").value("APPROVED"))
                        .andExpect(jsonPath("$.failover.occurred").value(true)));
        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(
                                get("/api/v1/tenant/verification-incidents").with(tenantAdmin(TENANT_A)))
                        .andExpect(jsonPath("$[0].provider").value("simulated-unavailable"))
                        .andExpect(jsonPath("$[0].continuedWith").value("simulated-approve"))
                        .andExpect(jsonPath("$[0].verificationId").value(firstId)));

        // Isolamento entre tenants.
        mvc.perform(get("/api/v1/verifications/{id}", firstId).with(tenant(TENANT_B, "verification:read")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/tenant/verification-policy").with(tenantAdmin(TENANT_B)))
                .andExpect(jsonPath("$.rules[?(@.type == 'FACE_MATCH')]").isEmpty());

        // Retorno bruto do provedor fica cifrado no event store.
        eventStore.readStream("verification-" + firstId).stream()
                .filter(e -> e.type().startsWith("ProviderResponseReceived"))
                .forEach(e -> assertThat(new String(e.data(), StandardCharsets.ISO_8859_1))
                        .doesNotContain("transactionId"));

        // Eliminação do titular remove os resultados de validação.
        mvc.perform(delete("/api/v1/customers/{id}", customerId).with(tenant(TENANT_A, "customer:erase")))
                .andExpect(status().isAccepted());
        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(
                                get("/api/v1/verifications/{id}", firstId).with(tenant(TENANT_A, "verification:read")))
                        .andExpect(status().isNotFound()));
    }

    @Test
    void failsOnlyWhenNoProviderAnswersAndValidatesConfiguration() throws Exception {
        var customerId = register(tenant(TENANT_B, "customer:write"), "39053344705");

        verify(TENANT_B, customerId, "LIVENESS")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VERIFICATION_NOT_CONFIGURED"));

        configure(TENANT_B, "LIVENESS", "simulated-unavailable", null, "Teste sem contingência")
                .andExpect(status().isOk());
        verify(TENANT_B, customerId, "LIVENESS")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("FAILED"))
                .andExpect(jsonPath("$.reasonCodes", hasItem("ALL_PROVIDERS_UNAVAILABLE")));

        configure(TENANT_B, "LIVENESS", "provedor-inexistente", null, "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROVIDER_NOT_AVAILABLE"));
        configure(TENANT_B, "LIVENESS", "simulated-approve", "simulated-approve", "x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FALLBACK"));
        configure(TENANT_B, "LIVENESS", "simulated-approve", null, "").andExpect(status().isBadRequest());

        // Cliente de outro tenant não pode ser validado.
        var otherTenantCustomer = register(tenant(TENANT_A, "customer:write"), "86288366757");
        verify(TENANT_B, otherTenantCustomer, "LIVENESS").andExpect(status().isNotFound());

        // Token de aplicação (sem role tenant-admin) não administra a política.
        mvc.perform(get("/api/v1/tenant/verification-policy")
                        .with(tenant(TENANT_B, "customer:read", "verification:write")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tenant/verification-providers").with(tenantAdmin(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("simulated-approve")));
    }

    private org.springframework.test.web.servlet.ResultActions configure(
            String tenantId, String type, String primary, String fallback, String reason) throws Exception {
        var body = """
                {"primaryProvider":"%s",%s"minimumScore":70,"onInconclusive":"MANUAL_REVIEW","enabled":true,"reason":"%s"}
                """.formatted(primary, fallback == null ? "" : "\"fallbackProvider\":\"" + fallback + "\",", reason);
        return mvc.perform(put("/api/v1/tenant/verification-policy/rules/{type}", type)
                .with(tenantAdmin(tenantId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions verify(String tenantId, String customerId, String type)
            throws Exception {
        JwtRequestPostProcessor token = tenant(tenantId, "verification:write");
        return mvc.perform(post("/api/v1/verifications")
                .with(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"%s\",\"type\":\"%s\"}".formatted(customerId, type)));
    }
}
