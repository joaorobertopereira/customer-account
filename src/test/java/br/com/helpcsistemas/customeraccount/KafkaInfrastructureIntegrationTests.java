package br.com.helpcsistemas.customeraccount;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class KafkaInfrastructureIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Test
    void publishesToTheConfiguredKafkaBroker() throws Exception {
        var topic = "customer-account.infrastructure-test";
        var result = kafka.send(topic, "tenant-a", UUID.randomUUID().toString()).get(10, TimeUnit.SECONDS);

        assertThat(result.getRecordMetadata().topic()).isEqualTo(topic);
        assertThat(result.getRecordMetadata().offset()).isGreaterThanOrEqualTo(0);
    }
}
