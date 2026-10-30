package br.com.helpcsistemas.customeraccount.secrets.internal;

import br.com.helpcsistemas.customeraccount.secrets.SecretStore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Vault KV v2 ({@code GET /v1/<mount>/data/<base>/<path>}) com cache curto: rotação de credencial no Vault é
 * percebida em até {@code app.secrets.cache-ttl}.
 */
class VaultKvSecretStore implements SecretStore {

    private final RestClient rest;
    private final SecretsProperties.Vault vault;
    private final Cache<String, Optional<Map<String, String>>> cache;

    VaultKvSecretStore(RestClient.Builder builder, SecretsProperties.Vault vault, Duration cacheTtl) {
        this.vault = vault;
        var http = HttpClient.newBuilder().connectTimeout(vault.timeout()).build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(vault.timeout());
        this.rest = builder.clone()
                .baseUrl(vault.uri())
                .requestFactory(requestFactory)
                .build();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(1_000)
                .build();
    }

    @Override
    public Optional<Map<String, String>> read(String path) {
        return cache.get(path, this::fetch);
    }

    private Optional<Map<String, String>> fetch(String path) {
        // Caminho montado sem template: variáveis de URI codificariam as barras do caminho do segredo.
        var response = rest.get()
                .uri(uri -> uri.path("/v1/" + vault.kvMount() + "/data/" + vault.basePath() + "/" + path)
                        .build())
                .header("X-Vault-Token", token())
                .exchange((request, res) ->
                        res.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND) ? null : res.bodyTo(KvResponse.class));
        return Optional.ofNullable(response)
                .map(KvResponse::data)
                .map(KvData::data)
                .map(Map::copyOf);
    }

    private String token() {
        if (vault.tokenFile() != null && !vault.tokenFile().isBlank()) {
            try {
                return Files.readString(Path.of(vault.tokenFile())).trim();
            } catch (IOException ex) {
                throw new UncheckedIOException("Não foi possível ler o token do Vault", ex);
            }
        }
        if (vault.token() == null || vault.token().isBlank()) {
            throw new IllegalStateException("Configure app.secrets.vault.token ou app.secrets.vault.token-file");
        }
        return vault.token();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KvResponse(KvData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KvData(Map<String, String> data) {}
}
