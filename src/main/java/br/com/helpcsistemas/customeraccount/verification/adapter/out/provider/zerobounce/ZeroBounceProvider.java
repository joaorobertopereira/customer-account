package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.zerobounce;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderCredentialsResolver;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvider;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** ZeroBounce: qualidade do e-mail (existência, descartável, armadilhas de spam). Credencial: {@code api_key}. */
class ZeroBounceProvider implements VerificationProvider {

    static final ProviderId ID = new ProviderId("zerobounce");

    private final RestClient rest;
    private final ProviderCredentialsResolver credentials;
    private final JsonMapper json;

    ZeroBounceProvider(RestClient rest, ProviderCredentialsResolver credentials, JsonMapper json) {
        this.rest = rest;
        this.credentials = credentials;
        this.json = json;
    }

    @Override
    public ProviderId id() {
        return ID;
    }

    @Override
    public Set<VerificationType> supportedTypes() {
        return Set.of(VerificationType.EMAIL_QUALITY);
    }

    @Override
    public ProviderResult verify(ProviderRequest request) {
        var creds = credentials.resolve(ID, request.tenantId());
        // URI template: métricas/traces registram o template, nunca a chave nem o e-mail.
        var raw = rest.get()
                .uri(
                        "/v2/validate?api_key={key}&email={email}&ip_address=",
                        creds.require("api_key"),
                        request.subject().email())
                .retrieve()
                .body(String.class);
        var validation = json.readValue(raw, Validation.class);
        var status =
                validation.status() == null ? "unknown" : validation.status().toLowerCase(Locale.ROOT);

        var reasons = new ArrayList<String>();
        if (!"valid".equals(status)) {
            reasons.add("STATUS_" + normalize(status));
        }
        if (validation.subStatus() != null && !validation.subStatus().isBlank()) {
            reasons.add("SUB_STATUS_" + normalize(validation.subStatus()));
        }
        return switch (status) {
            case "valid" -> new ProviderResult(ProviderDecision.APPROVED, 95, reasons, null, 0, raw);
            case "catch-all", "unknown" -> new ProviderResult(ProviderDecision.INCONCLUSIVE, 50, reasons, null, 0, raw);
            default -> new ProviderResult(ProviderDecision.REJECTED, 5, reasons, null, 0, raw);
        };
    }

    private static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Validation(
            String status, @JsonProperty("sub_status") String subStatus) {}
}
