package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.twilio;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderCredentialsResolver;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.VerificationProvider;
import br.com.helpcsistemas.customeraccount.verification.application.port.out.ProviderRequest;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderDecision;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderId;
import br.com.helpcsistemas.customeraccount.verification.domain.model.ProviderResult;
import br.com.helpcsistemas.customeraccount.verification.domain.model.VerificationType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Twilio Verify: posse do celular por código SMS. O código é gerado, enviado e checado pela Twilio (não trafega por
 * nós no envio). Credenciais: {@code account_sid}, {@code auth_token}, {@code service_sid}.
 */
class TwilioVerifyProvider implements VerificationProvider {

    static final ProviderId ID = new ProviderId("twilio-verify");

    private final RestClient rest;
    private final ProviderCredentialsResolver credentials;
    private final JsonMapper json;

    TwilioVerifyProvider(RestClient rest, ProviderCredentialsResolver credentials, JsonMapper json) {
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
        return Set.of(VerificationType.MOBILE_PHONE_OWNERSHIP);
    }

    @Override
    public ProviderResult verify(ProviderRequest request) {
        var creds = credentials.resolve(ID, request.tenantId());
        var form = new LinkedMultiValueMap<String, String>();
        form.add("To", request.subject().mobilePhone());
        form.add("Channel", "sms");
        var raw = rest.post()
                .uri("/v2/Services/{service}/Verifications", creds.require("service_sid"))
                .headers(h -> h.setBasicAuth(creds.require("account_sid"), creds.require("auth_token")))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        var verification = json.readValue(raw, Verification.class);
        return new ProviderResult(ProviderDecision.CHALLENGE_SENT, null, List.of(), verification.sid(), 0, raw);
    }

    @Override
    public ProviderResult confirm(ProviderRequest request, String transactionId, String answer) {
        var creds = credentials.resolve(ID, request.tenantId());
        var form = new LinkedMultiValueMap<String, String>();
        form.add("VerificationSid", transactionId);
        form.add("Code", answer);
        try {
            var raw = rest.post()
                    .uri("/v2/Services/{service}/VerificationCheck", creds.require("service_sid"))
                    .headers(h -> h.setBasicAuth(creds.require("account_sid"), creds.require("auth_token")))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            var check = json.readValue(raw, Verification.class);
            return "approved".equals(check.status())
                    ? new ProviderResult(ProviderDecision.APPROVED, 100, List.of(), transactionId, 0, raw)
                    : new ProviderResult(ProviderDecision.REJECTED, 0, List.of("INVALID_CODE"), transactionId, 0, raw);
        } catch (HttpClientErrorException.NotFound ex) {
            // A Twilio responde 404 quando a verificação expirou, já foi aprovada ou excedeu tentativas.
            return new ProviderResult(
                    ProviderDecision.REJECTED,
                    0,
                    List.of("CHALLENGE_NOT_FOUND"),
                    transactionId,
                    0,
                    ex.getResponseBodyAsString());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verification(String sid, String status) {}
}
