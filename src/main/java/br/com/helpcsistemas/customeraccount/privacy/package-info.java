/**
 * Proteção de dados pessoais (LGPD by design): criptografia de envelope por titular (uma DEK por titular, cifrada
 * por uma KEK no Vault Transit), crypto-shredding para o direito de eliminação (art. 18, VI) e blind index (HMAC)
 * para busca/unicidade sem armazenar o dado em claro.
 */
@ApplicationModule(displayName = "Privacy", allowedDependencies = "shared")
package br.com.helpcsistemas.customeraccount.privacy;

import org.springframework.modulith.ApplicationModule;
