package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.hosted;

import br.com.helpcsistemas.customeraccount.shared.domain.InvalidValueException;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.HostedCallbackProvider;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderCredentialsResolver;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.HostedSession;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.config.HostedProvidersProperties.Provider;
import br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Provedor de sessão hospedada parametrizado por properties, sem assumir contrato específico de fornecedor. */
public class GenericHostedProvider implements HostedCallbackProvider {

    private final ProviderId id;
    private final Provider settings;
    private final RestClient rest;
    private final ProviderCredentialsResolver credentials;
    private final JsonMapper json;
    private final Clock clock;

    public GenericHostedProvider(
            ProviderId id,
            Provider settings,
            RestClient rest,
            ProviderCredentialsResolver credentials,
            JsonMapper json,
            Clock clock) {
        this.id = id;
        this.settings = settings;
        this.rest = rest;
        this.credentials = credentials;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public ProviderId id() {
        return id;
    }

    @Override
    public Set<VerificationType> supportedTypes() {
        return Set.of(VerificationType.IDENTITY);
    }

    @Override
    public HostedSession start(ProviderRequest request, URI returnUrl) {
        if (returnUrl != null && !"https".equalsIgnoreCase(returnUrl.getScheme())) {
            throw new InvalidValueException("INVALID_RETURN_URL", "returnUrl deve usar https");
        }
        var target = json.createObjectNode();
        for (var field : settings.requestFields().entrySet()) {
            var value = requestValue(field.getValue(), request, returnUrl);
            if (value != null) {
                target.put(field.getKey(), value);
            }
        }
        var raw = rest.post()
                .uri(settings.createSessionPath())
                .headers(headers -> addAuthorization(headers, request))
                .body(json.writeValueAsString(target))
                .retrieve()
                .body(String.class);
        var response = json.readTree(raw);
        var sessionId = requiredText(response, settings.sessionIdPointer());
        var url = requiredText(response, settings.urlPointer());
        var expiresAt = Instant.parse(requiredText(response, settings.expiresAtPointer()));
        var uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null) {
            throw new IllegalStateException("Provedor retornou um link hospedado inválido");
        }
        return new HostedSession(
                sessionId,
                uri.toString(),
                expiresAt,
                settings.requestFields().values().stream().distinct().sorted().toList());
    }

    @Override
    public ProviderResult verify(ProviderRequest request) {
        var session = start(request, null);
        return new ProviderResult(
                ProviderDecision.CHALLENGE_SENT, null, List.of(), session.sessionId(), 0, "hosted-session");
    }

    public boolean authenticateCallback(Map<String, String> headers, byte[] body, String tenantId) {
        var callback = settings.callback();
        if (callback == null || callback.signatureHeader() == null || callback.timestampHeader() == null) {
            return false;
        }
        var timestamp = header(headers, callback.timestampHeader());
        var signature = header(headers, callback.signatureHeader());
        if (timestamp == null || signature == null) {
            return false;
        }
        try {
            var signedAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            if (Duration.between(signedAt, clock.instant()).abs().compareTo(callback.tolerance()) > 0) {
                return false;
            }
            var secret = credentials
                    .resolve(id, br.com.helpcsistemas.customeraccount.shared.domain.TenantId.of(tenantId))
                    .require("webhook_secret");
            var expected = sign(secret, timestamp + "." + new String(body, StandardCharsets.UTF_8));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public HostedResult parseCallback(byte[] body) {
        var callback = settings.callback();
        if (callback == null) {
            throw new IllegalStateException("Callback não configurado");
        }
        var raw = new String(body, StandardCharsets.UTF_8);
        var root = json.readTree(raw);
        var statusText = requiredText(root, callback.statusPointer());
        var status = callback.statusMap().get(statusText);
        if (status == null) {
            throw new IllegalArgumentException("Status do callback não mapeado");
        }
        return new HostedResult(
                requiredText(root, callback.sessionIdPointer()),
                status,
                integer(root, callback.scorePointer()),
                strings(root, callback.reasonCodesPointer()),
                strings(root, callback.fraudSignalsPointer()),
                text(root, callback.eventIdPointer()),
                instant(root, callback.occurredAtPointer()),
                raw);
    }

    private void addAuthorization(org.springframework.http.HttpHeaders headers, ProviderRequest request) {
        var token = credentials.resolve(id, request.tenantId()).require("api_token");
        switch (settings.authScheme().toUpperCase(Locale.ROOT)) {
            case "BEARER" -> headers.setBearerAuth(token);
            case "API_KEY_HEADER" -> headers.set(settings.authHeader(), token);
            case "BASIC" ->
                headers.setBasicAuth(
                        credentials.resolve(id, request.tenantId()).require("client_id"),
                        credentials.resolve(id, request.tenantId()).require("client_secret"));
            default -> throw new IllegalStateException("Esquema de autenticação genérico não suportado");
        }
    }

    private static String requestValue(String key, ProviderRequest request, URI returnUrl) {
        return switch (key) {
            case "reference" -> request.verificationId().toString();
            case "cpf" -> request.subject().cpf();
            case "fullName" -> request.subject().fullName();
            case "birthDate" -> request.subject().birthDate().toString();
            case "email" -> request.subject().email();
            case "mobilePhone" -> request.subject().mobilePhone();
            case "returnUrl" -> returnUrl == null ? null : returnUrl.toString();
            default -> throw new IllegalArgumentException("Campo de requisição hospedada não permitido: " + key);
        };
    }

    private static String requiredText(JsonNode root, String pointer) {
        var value = text(root, pointer);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Resposta do provedor incompleta");
        }
        return value;
    }

    private static String text(JsonNode root, String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return null;
        }
        var node = root.at(pointer);
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }

    private static Integer integer(JsonNode root, String pointer) {
        var node = pointer == null ? null : root.at(pointer);
        return node == null || node.isMissingNode() || !node.isNumber() ? null : node.intValue();
    }

    private static List<String> strings(JsonNode root, String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return List.of();
        }
        var node = root.at(pointer);
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return List.of(node.asString());
        }
        var values = new ArrayList<String>();
        node.forEach(value -> values.add(value.asString()));
        return List.copyOf(values);
    }

    private Instant instant(JsonNode root, String pointer) {
        var value = text(root, pointer);
        return value == null ? clock.instant() : Instant.parse(value);
    }

    private static String header(Map<String, String> headers, String name) {
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
