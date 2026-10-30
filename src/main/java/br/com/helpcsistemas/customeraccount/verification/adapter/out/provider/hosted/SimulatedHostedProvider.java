package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.hosted;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.HostedCallbackProvider;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSession;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.SimulatedCallbackSignerPort;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultStatus;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Local-only simulation. The secret is public and must never be enabled in production. */
public class SimulatedHostedProvider implements HostedCallbackProvider, SimulatedCallbackSignerPort {

    public static final ProviderId ID = ProviderId.of("simulated-hosted");
    private final String baseUrl;
    private final String secret;
    private final JsonMapper json;
    private final Clock clock;

    public SimulatedHostedProvider(String baseUrl, String secret, JsonMapper json, Clock clock) {
        this.baseUrl = baseUrl;
        this.secret = secret;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public ProviderId id() {
        return ID;
    }

    @Override
    public Set<VerificationType> supportedTypes() {
        return Set.of(VerificationType.IDENTITY);
    }

    @Override
    public HostedSession start(ProviderRequest request, URI returnUrl) {
        var sessionId = java.util.UUID.randomUUID().toString();
        var origin = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new HostedSession(
                sessionId,
                origin + "/dev/hosted-verification/" + sessionId,
                clock.instant().plus(Duration.ofMinutes(30)),
                List.of("cpf", "fullName", "birthDate"));
    }

    @Override
    public ProviderResult verify(ProviderRequest request) {
        var session = start(request, null);
        return new ProviderResult(
                ProviderDecision.CHALLENGE_SENT, null, List.of(), session.sessionId(), 0, "simulated");
    }

    @Override
    public boolean authenticateCallback(Map<String, String> headers, byte[] body, String tenantId) {
        var timestamp = value(headers, "X-Timestamp");
        var signature = value(headers, "X-Signature");
        if (timestamp == null || signature == null) {
            return false;
        }
        try {
            if (Math.abs(clock.instant().getEpochSecond() - Long.parseLong(timestamp))
                    > Duration.ofMinutes(5).toSeconds()) {
                return false;
            }
            var expected = sign(secret, timestamp + "." + new String(body, StandardCharsets.UTF_8));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public HostedResult parseCallback(byte[] body) {
        var raw = new String(body, StandardCharsets.UTF_8);
        JsonNode node = json.readTree(raw);
        return new HostedResult(
                node.get("sessionId").asString(),
                HostedResultStatus.valueOf(node.get("status").asString()),
                node.has("score") && !node.get("score").isNull()
                        ? node.get("score").intValue()
                        : null,
                node.has("reasonCodes") ? strings(node.get("reasonCodes")) : List.of(),
                node.has("fraudSignals") ? strings(node.get("fraudSignals")) : List.of(),
                node.get("eventId").asString(),
                node.has("occurredAt")
                        ? java.time.Instant.parse(node.get("occurredAt").asString())
                        : clock.instant(),
                raw);
    }

    public String signature(String timestamp, String body) {
        return sign(secret, timestamp + "." + body);
    }

    private static List<String> strings(JsonNode node) {
        var values = new java.util.ArrayList<String>();
        node.forEach(value -> values.add(value.asString()));
        return List.copyOf(values);
    }

    private static String value(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static String sign(String secret, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "v1=" + Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
