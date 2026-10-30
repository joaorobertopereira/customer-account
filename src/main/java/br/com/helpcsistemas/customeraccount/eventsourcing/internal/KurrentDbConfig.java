package br.com.helpcsistemas.customeraccount.eventsourcing.internal;

import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBConnectionString;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class KurrentDbConfig {

    /** O client conecta sob demanda (gRPC) e é thread-safe: uma instância por aplicação. */
    @Bean(destroyMethod = "shutdown")
    KurrentDBClient kurrentDbClient(EventStoreProperties properties) {
        return KurrentDBClient.create(KurrentDBConnectionString.parseOrThrow(properties.connectionString()));
    }
}
