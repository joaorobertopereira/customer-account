package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider;

import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** RestClient por provedor: base URL e timeouts próprios, sem seguir redirects; herda tracing/métricas do Boot. */
public final class ProviderHttpClients {

    private ProviderHttpClients() {}

    public static RestClient create(RestClient.Builder builder, VerificationProvidersProperties.Http settings) {
        var http = HttpClient.newBuilder()
                .connectTimeout(settings.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(http);
        requestFactory.setReadTimeout(settings.timeout());
        return builder.clone()
                .baseUrl(settings.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
