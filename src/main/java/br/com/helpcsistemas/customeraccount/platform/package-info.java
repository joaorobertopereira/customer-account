/**
 * Preocupações transversais de infraestrutura: segurança (resource server + resolução de tenant), observabilidade,
 * logging de fluxo, OpenAPI e relógio. Nenhum módulo de negócio depende deste módulo.
 */
@ApplicationModule(displayName = "Platform", allowedDependencies = "shared")
package br.com.helpcsistemas.customeraccount.platform;

import org.springframework.modulith.ApplicationModule;
