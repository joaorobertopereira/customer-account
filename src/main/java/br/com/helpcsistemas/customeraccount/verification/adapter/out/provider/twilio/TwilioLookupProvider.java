package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.twilio;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderCredentialsResolver;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvider;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Twilio Lookup v2: risco da linha. Rejeita linhas que não são móveis; troca recente de SIM (sinal clássico de
 * sequestro de conta) vira {@code INCONCLUSIVE} e segue a ação configurada pelo tenant.
 */
class TwilioLookupProvider implements VerificationProvider {

    static final ProviderId ID = new ProviderId("twilio-lookup");

    private final RestClient rest;
    private final ProviderCredentialsResolver credentials;
    private final JsonMapper json;

    TwilioLookupProvider(RestClient rest, ProviderCredentialsResolver credentials, JsonMapper json) {
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
        return Set.of(VerificationType.MOBILE_PHONE_RISK);
    }

    @Override
    public ProviderResult verify(ProviderRequest request) {
        var creds = credentials.resolve(ID, request.tenantId());
        var raw = rest.get()
                .uri(
                        "/v2/PhoneNumbers/{phone}?Fields={fields}",
                        request.subject().mobilePhone(),
                        "line_type_intelligence,sim_swap")
                .headers(h -> h.setBasicAuth(creds.require("account_sid"), creds.require("auth_token")))
                .retrieve()
                .body(String.class);
        var lookup = json.readValue(raw, Lookup.class);

        if (!Boolean.TRUE.equals(lookup.valid())) {
            return result(ProviderDecision.REJECTED, 0, "INVALID_NUMBER", raw);
        }
        var lineType = lookup.lineType() == null ? null : lookup.lineType().type();
        if (lineType == null) {
            return result(ProviderDecision.INCONCLUSIVE, 50, "LINE_TYPE_UNKNOWN", raw);
        }
        if (!"mobile".equals(lineType)) {
            return result(ProviderDecision.REJECTED, 10, "LINE_TYPE_" + lineType.toUpperCase(Locale.ROOT), raw);
        }
        if (lookup.simSwap() != null
                && lookup.simSwap().lastSimSwap() != null
                && Boolean.TRUE.equals(lookup.simSwap().lastSimSwap().swappedInPeriod())) {
            return result(ProviderDecision.INCONCLUSIVE, 40, "RECENT_SIM_SWAP", raw);
        }
        return new ProviderResult(ProviderDecision.APPROVED, 90, List.of(), null, 0, raw);
    }

    private static ProviderResult result(ProviderDecision decision, int score, String reason, String raw) {
        return new ProviderResult(decision, score, List.of(reason), null, 0, raw);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Lookup(
            Boolean valid,
            @JsonProperty("line_type_intelligence") LineType lineType,
            @JsonProperty("sim_swap") SimSwap simSwap) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LineType(String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SimSwap(@JsonProperty("last_sim_swap") LastSimSwap lastSimSwap) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LastSimSwap(@JsonProperty("swapped_in_period") Boolean swappedInPeriod) {}
}
