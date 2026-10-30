package br.com.helpcsistemas.customeraccount;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.helpcsistemas.customeraccount.eventsourcing.EventStore;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * Base dos testes de integração: KurrentDB e MongoDB reais (containers compartilhados por todas as classes, um único
 * contexto Spring). As subclasses usam {@code @Testcontainers(disabledWithoutDocker = true)}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    static final Duration EVENTUAL = Duration.ofSeconds(15);

    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:8.0");

    static final GenericContainer<?> KURRENTDB = new GenericContainer<>(
                    System.getProperty("kurrentdb.image", "kurrentplatform/kurrentdb:25.1"))
            .withEnv("KURRENTDB_INSECURE", "true")
            .withEnv("KURRENTDB_MEM_DB", "true")
            .withEnv("KURRENTDB_RUN_PROJECTIONS", "None")
            .withExposedPorts(2113)
            .waitingFor(Wait.forLogMessage(".*System startup tasks completed.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    /** SMTP de teste: captura os e-mails de OTP e expõe a API HTTP (8025) para lermos o código. */
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:latest")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/livez").forPort(8025));

    static {
        MONGO.start();
        KURRENTDB.start();
        MAILPIT.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", () -> MONGO.getReplicaSetUrl("customer_account"));
        registry.add(
                "app.eventstore.connection-string",
                () -> "kurrentdb://%s:%d?tls=false".formatted(KURRENTDB.getHost(), KURRENTDB.getMappedPort(2113)));
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
    }

    static String mailpitApi() {
        return "http://%s:%d/api/v1".formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(8025));
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MongoTemplate mongo;

    @Autowired
    protected EventStore eventStore;

    /** Token de aplicação tenant (client credentials) com os escopos informados. */
    static JwtRequestPostProcessor tenant(String tenantId, String... scopes) {
        var authorities = Arrays.stream(scopes)
                .map(scope -> (GrantedAuthority) new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();
        return jwt().jwt(token -> token.claim("tenant_id", tenantId).claim("azp", tenantId))
                .authorities(authorities);
    }

    /** Token de usuário administrador do tenant (console do tenant). */
    static JwtRequestPostProcessor tenantAdmin(String tenantId) {
        return jwt().jwt(token -> token.claim("tenant_id", tenantId)
                        .claim("azp", tenantId + "-admin")
                        .claim("preferred_username", "admin." + tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_tenant-admin"));
    }

    String register(JwtRequestPostProcessor token, String cpf) throws Exception {
        var response = mvc.perform(post("/api/v1/customers")
                        .with(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationBody(cpf)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    static String registrationBody(String cpf) {
        return """
                {
                  "cpf": "%s",
                  "personalData": {
                    "fullName": "Maria da Silva Souza",
                    "birthDate": "1990-05-20",
                    "motherName": "Ana da Silva",
                    "maritalStatus": "MARRIED",
                    "nationality": "BR"
                  },
                  "email": "maria.souza@example.com",
                  "mobilePhone": "(11) 91234-5678",
                  "address": {
                    "zipCode": "01310-100",
                    "street": "Avenida Paulista",
                    "number": "1000",
                    "district": "Bela Vista",
                    "city": "São Paulo",
                    "state": "SP"
                  }
                }
                """.formatted(cpf);
    }
}
