package br.com.helpcsistemas.customeraccount.privacy.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * KEK no HashiCorp Vault / OpenBao (Transit). A chave mestra nunca sai do Vault: a aplicação só recebe a DEK
 * (endpoint {@code datakey/plaintext}) e pede para desembrulhá-la ({@code decrypt}). O token deve ter política de
 * menor privilégio (somente essas duas operações nesta chave).
 */
class VaultTransitKeyEncryptionService implements KeyEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(VaultTransitKeyEncryptionService.class);
    private static final String TOKEN_HEADER = "X-Vault-Token";

    private final RestClient rest;
    private final PrivacyProperties.Vault vault;

    VaultTransitKeyEncryptionService(RestClient.Builder builder, PrivacyProperties.Vault vault) {
        this.vault = vault;
        var http = HttpClient.newBuilder().connectTimeout(vault.timeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(vault.timeout());
        this.rest = builder.clone()
                .baseUrl(vault.uri())
                .requestFactory(requestFactory)
                .build();
        log.atInfo()
                .addKeyValue("vault.uri", vault.uri())
                .addKeyValue("vault.key", vault.keyName())
                .log("KEK no Vault Transit configurada");
    }

    @Override
    @Retry(name = "vault")
    @CircuitBreaker(name = "vault")
    public DataKey generateDataKey() {
        var response = rest.post()
                .uri("/v1/{mount}/datakey/plaintext/{key}", vault.transitMount(), vault.keyName())
                .header(TOKEN_HEADER, token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("bits", 256))
                .retrieve()
                .body(VaultResponse.class);
        var data = requireData(response);
        log.debug("DEK gerada no Vault Transit");
        return new DataKey(Base64.getDecoder().decode(data.plaintext()), data.ciphertext());
    }

    @Override
    @Retry(name = "vault")
    @CircuitBreaker(name = "vault")
    public byte[] unwrap(String wrappedKey) {
        var response = rest.post()
                .uri("/v1/{mount}/decrypt/{key}", vault.transitMount(), vault.keyName())
                .header(TOKEN_HEADER, token())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("ciphertext", wrappedKey))
                .retrieve()
                .body(VaultResponse.class);
        return Base64.getDecoder().decode(requireData(response).plaintext());
    }

    @Override
    public String providerName() {
        return "vault-transit";
    }

    /** Token lido a cada chamada: permite rotação do arquivo (ex.: Vault Agent) sem restart. */
    private String token() {
        if (vault.tokenFile() != null && !vault.tokenFile().isBlank()) {
            try {
                return Files.readString(Path.of(vault.tokenFile())).trim();
            } catch (IOException ex) {
                throw new UncheckedIOException("Não foi possível ler o token do Vault", ex);
            }
        }
        if (vault.token() == null || vault.token().isBlank()) {
            throw new IllegalStateException("Configure app.privacy.vault.token ou app.privacy.vault.token-file");
        }
        return vault.token();
    }

    private static VaultData requireData(VaultResponse response) {
        if (response == null || response.data() == null || response.data().plaintext() == null) {
            throw new IllegalStateException("Resposta inválida do Vault Transit");
        }
        return response.data();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VaultResponse(VaultData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VaultData(String plaintext, String ciphertext) {}
}
