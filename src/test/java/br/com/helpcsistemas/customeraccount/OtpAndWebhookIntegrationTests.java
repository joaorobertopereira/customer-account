package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.helpcsistemas.customeraccount.tenant.domain.model.WebhookSignature;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Valida\u00e7\u00e3o de posse do e-mail com OTP pr\u00f3prio (c\u00f3digo lido do Mailpit) e entrega do resultado ao webhook do tenant
 * (assinatura HMAC, retentativa ap\u00f3s falha e registro da entrega no processo de valida\u00e7\u00e3o).
 */
@Testcontainers(disabledWithoutDocker = true)
class OtpAndWebhookIntegrationTests extends IntegrationTestSupport {

    private static final String TENANT = "tenant-app-c";
    private static final Pattern CODE = Pattern.compile("c\u00f3digo de verifica\u00e7\u00e3o \u00e9: (\\d{6})");

    private static HttpServer receiver;
    private static final ConcurrentLinkedQueue<Received> received = new ConcurrentLinkedQueue<>();
    /** A primeira entrega recebe 503 para exercitar a retentativa. */
    private static final AtomicInteger calls = new AtomicInteger();

    record Received(Map<String, String> headers, String body) {}

    @BeforeAll
    static void startReceiver() throws Exception {
        receiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        receiver.createContext("/hook", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var h = exchange.getRequestHeaders();
            received.add(new Received(
                    Map.of(
                            "id", h.getFirst("X-CA-Event-Id"),
                            "type", h.getFirst("X-CA-Event-Type"),
                            "ts", h.getFirst("X-CA-Timestamp"),
                            "sig", h.getFirst("X-CA-Signature")),
                    body));
            exchange.sendResponseHeaders(calls.incrementAndGet() == 1 ? 503 : 204, -1);
            exchange.close();
        });
        receiver.start();
    }

    @AfterAll
    static void stopReceiver() {
        receiver.stop(0);
    }

    @Test
    void emailOwnershipWithOtpAndSignedWebhookDelivery() throws Exception {
        var url = "http://localhost:%d/hook".formatted(receiver.getAddress().getPort());
        var webhook = mvc.perform(put("/api/v1/tenant/webhook")
                        .with(tenantAdmin(TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"%s\"}".formatted(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updatedBy").value("admin." + TENANT))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String secret = JsonPath.read(webhook, "$.secret");
        assertThat(secret).startsWith("whsec_");
        // O segredo s\u00f3 aparece na gera\u00e7\u00e3o.
        mvc.perform(get("/api/v1/tenant/webhook").with(tenantAdmin(TENANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist());

        mvc.perform(put("/api/v1/tenant/verification-policy/rules/EMAIL_OWNERSHIP")
                        .with(tenantAdmin(TENANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"primaryProvider":"own-email-otp","onInconclusive":"REJECT","enabled":true,"reason":"OTP"}
                                """))
                .andExpect(status().isOk());
        var customerId = register(tenant(TENANT, "customer:write"), "71428793860");

        var pending = verify(customerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.outcome").doesNotExist())
                .andExpect(jsonPath("$.challenge.remainingAttempts").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String verificationId = JsonPath.read(pending, "$.id");
        var code = await().atMost(EVENTUAL).until(OtpAndWebhookIntegrationTests::latestOtpCode, c -> c != null);

        confirm(verificationId, wrongCode(code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.challenge.remainingAttempts").value(4));
        confirm(verificationId, "12ab").andExpect(status().isBadRequest());
        confirm(verificationId, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.provider").value("own-email-otp"));
        confirm(verificationId, code)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VERIFICATION_NOT_AWAITING_CONFIRMATION"));

        // Webhook: 1ª tentativa recebe 503 e é reagendada (30s); antecipamos a retentativa direto na fila.
        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(
                                get("/api/v1/tenant/webhook/deliveries").with(tenantAdmin(TENANT)))
                        .andExpect(jsonPath("$[0].status").value("PENDING"))
                        .andExpect(jsonPath("$[0].attempts").value(1))
                        .andExpect(jsonPath("$[0].lastHttpStatus").value(503)));
        mongo.updateMulti(
                Query.query(Criteria.where("tenantId").is(TENANT).and("status").is("PENDING")),
                new Update().set("nextAttemptAt", Instant.EPOCH),
                "tenant_notifications");
        await().atMost(EVENTUAL).until(() -> calls.get() >= 2);

        var delivery = received.stream().reduce((first, second) -> second).orElseThrow();
        assertThat(delivery.headers()).containsEntry("type", "verification.completed");
        assertThat(delivery.headers().get("sig"))
                .isEqualTo(WebhookSignature.sign(
                        secret, Long.parseLong(delivery.headers().get("ts")), delivery.body()));
        assertThat((String) JsonPath.read(delivery.body(), "$.data.verificationId"))
                .isEqualTo(verificationId);
        assertThat((String) JsonPath.read(delivery.body(), "$.data.outcome")).isEqualTo("APPROVED");
        assertThat(delivery.body()).doesNotContain("maria.souza@example.com");

        await().atMost(EVENTUAL)
                .untilAsserted(() -> mvc.perform(get("/api/v1/verifications/{id}", verificationId)
                                .with(tenant(TENANT, "verification:read")))
                        .andExpect(jsonPath("$.delivery.deliveredAt").exists())
                        .andExpect(jsonPath("$.delivery.failed").value(false)));
        mvc.perform(get("/api/v1/tenant/webhook/deliveries").with(tenantAdmin(TENANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reference").value(verificationId))
                .andExpect(jsonPath("$[0].status").value("DELIVERED"))
                .andExpect(jsonPath("$[0].attempts").value(2));

        // Outro tenant n\u00e3o enxerga nem confirma a valida\u00e7\u00e3o.
        mvc.perform(post("/api/v1/verifications/{id}/confirmation", verificationId)
                        .with(tenant("tenant-app-a", "verification:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(code)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsafeWebhookConfigurationAndRequiresTenantAdmin() throws Exception {
        mvc.perform(put("/api/v1/tenant/webhook")
                        .with(tenantAdmin("tenant-app-d"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://user:pass@example.com/hook\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_URL"));
        mvc.perform(put("/api/v1/tenant/webhook")
                        .with(tenant("tenant-app-d", "verification:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/hook\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/tenant/webhook").with(tenantAdmin("tenant-app-d")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/tenant/webhook").with(tenantAdmin("tenant-app-d")))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/tenant/webhook")
                        .with(tenantAdmin("tenant-app-d"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/hook\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/tenant/webhook").with(tenantAdmin("tenant-app-d")))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tenant/webhook").with(tenantAdmin("tenant-app-d")))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    private ResultActions verify(String customerId) throws Exception {
        return mvc.perform(post("/api/v1/verifications")
                .with(tenant(TENANT, "verification:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"%s\",\"type\":\"EMAIL_OWNERSHIP\"}".formatted(customerId)));
    }

    private ResultActions confirm(String verificationId, String code) throws Exception {
        return mvc.perform(post("/api/v1/verifications/{id}/confirmation", verificationId)
                .with(tenant(TENANT, "verification:write"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)));
    }

    private static String wrongCode(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private static String latestOtpCode() throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            var response = http.send(
                    HttpRequest.newBuilder(URI.create(mailpitApi() + "/message/latest"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            var matcher = CODE.matcher((String) JsonPath.read(response.body(), "$.Text"));
            return matcher.find() ? matcher.group(1) : null;
        }
    }
}
