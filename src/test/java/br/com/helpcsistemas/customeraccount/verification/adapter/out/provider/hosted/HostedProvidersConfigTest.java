package br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.hosted;

import static br.com.helpcsistemas.customeraccount.verification.VerificationFixtures.CLOCK;
import static br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderTestSupport.credentials;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.HostedCallbackProvider;
import br.com.helpcsistemas.customeraccount.verification.adapter.out.provider.ProviderCredentialsResolver;
import br.com.helpcsistemas.customeraccount.verification.config.HostedProvidersProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Usa o application.yml real: o binding precisa casar com {@link HostedProvidersProperties}. */
class HostedProvidersConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(HostedProvidersConfigTest::loadApplicationYaml)
            .withUserConfiguration(PropertiesConfig.class, HostedProvidersConfig.class)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ProviderCredentialsResolver.class, () -> credentials(Map.of()))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(Clock.class, () -> CLOCK);

    @Test
    void bindsProvidersDeclaredInApplicationYaml() {
        runner.run(context -> {
            var properties = context.getBean(HostedProvidersProperties.class);
            assertThat(properties.providers()).containsKeys("legitimuz", "netrin");
            assertThat(properties.providers().get("legitimuz").baseUrl()).isEqualTo("https://legitimuz.invalid");
            assertThat(properties.providers().get("legitimuz").callback()).isNotNull();
            assertThat(context).doesNotHaveBean(HostedCallbackProvider.class);
        });
    }

    @Test
    void createsEnabledProviderFromApplicationYaml() {
        runner.withPropertyValues("VERIFICATION_LEGITIMUZ_ENABLED=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(HostedCallbackProvider.class);
            assertThat(context.getBean(HostedCallbackProvider.class).id().value())
                    .isEqualTo("legitimuz");
        });
    }

    private static void loadApplicationYaml(ConfigurableApplicationContext context) {
        try {
            var sources =
                    new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
            sources.forEach(
                    source -> context.getEnvironment().getPropertySources().addLast(source));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @EnableConfigurationProperties(HostedProvidersProperties.class)
    static class PropertiesConfig {}
}
