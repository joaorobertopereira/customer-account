/**
 * Leitura de segredos de integração (credenciais de provedores) em cofre externo. Vault KV v2 em produção; mapa em
 * configuração apenas para desenvolvimento/testes. Segredos nunca vão para eventos, read models ou logs.
 */
@ApplicationModule(displayName = "Secrets", allowedDependencies = "shared")
package br.com.helpcsistemas.customeraccount.secrets;

import org.springframework.modulith.ApplicationModule;
