package br.com.helpcsistemas.customeraccount.verification.adapter.in.web;

import br.com.helpcsistemas.customeraccount.verification.application.port.out.SimulatedCallbackSignerPort;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** Manual result controls are available only when simulated providers are enabled. */
@RestController
@RequestMapping("/dev/hosted-verification")
@ConditionalOnProperty(name = "app.verification.simulated-providers-enabled", havingValue = "true")
class SimulatedHostedVerificationController {

    private final SimulatedCallbackSignerPort signer;
    private final JsonMapper json;
    private final Clock clock;

    SimulatedHostedVerificationController(SimulatedCallbackSignerPort signer, JsonMapper json, Clock clock) {
        this.signer = signer;
        this.json = json;
        this.clock = clock;
    }

    @GetMapping(value = "/{sessionId}", produces = MediaType.TEXT_HTML_VALUE)
    String page(@PathVariable UUID sessionId) {
        var controls = java.util.Arrays.stream(
                        br.com.helpcsistemas.customeraccount.verification.domain.model.HostedResultStatus.values())
                .map(status -> "<form method=\"post\" action=\"/dev/hosted-verification/" + sessionId
                        + "/result\"><input type=\"hidden\" name=\"status\" value=\"" + status.name()
                        + "\"><button type=\"submit\">" + status.name() + "</button></form>")
                .reduce("", String::concat);
        return "<!doctype html><html><meta charset=\"utf-8\"><title>Simulação</title>"
                + "<h1>Simulação de validação hospedada</h1><p>Escolha o resultado de teste:</p>"
                + controls + "</html>";
    }

    @PostMapping(value = "/{sessionId}/result", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> submit(@PathVariable UUID sessionId, @RequestParam String status) {
        var body = json.writeValueAsBytes(new SimulatedCallback(
                UUID.randomUUID().toString(),
                sessionId.toString(),
                status,
                "FRAUD_DETECTED".equals(status) ? null : 90,
                "FRAUD_DETECTED".equals(status) ? java.util.List.of("SIMULATED_FRAUD") : java.util.List.of(),
                clock.instant()));
        var timestamp = Long.toString(clock.instant().getEpochSecond());
        var origin =
                ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        RestClient.create(origin)
                .post()
                .uri("/api/v1/provider-callbacks/simulated-hosted")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Timestamp", timestamp)
                .header("X-Signature", signer.signature(timestamp, new String(body, StandardCharsets.UTF_8)))
                .body(body)
                .retrieve()
                .toBodilessEntity();
        return ResponseEntity.status(303)
                .location(URI.create("/dev/hosted-verification/" + sessionId))
                .build();
    }

    record SimulatedCallback(
            String eventId,
            String sessionId,
            String status,
            Integer score,
            java.util.List<String> fraudSignals,
            java.time.Instant occurredAt) {}
}
