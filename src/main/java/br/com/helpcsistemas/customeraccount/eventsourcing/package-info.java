/**
 * Infraestrutura de Event Sourcing sobre o KurrentDB. Expõe uma API neutra ({@link
 * br.com.helpcsistemas.customeraccount.eventsourcing.EventStore}, {@link
 * br.com.helpcsistemas.customeraccount.eventsourcing.EventStreamSubscriber}) para que os módulos de negócio não
 * dependam do client do KurrentDB. Também propaga o contexto de trace (W3C traceparent) nos metadados dos eventos,
 * ligando a requisição que gravou o evento às projeções que o consomem.
 */
@ApplicationModule(displayName = "Event Sourcing", allowedDependencies = "shared")
package br.com.helpcsistemas.customeraccount.eventsourcing;

import org.springframework.modulith.ApplicationModule;
