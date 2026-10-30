/**
 * Shared kernel: tipos de domínio, base de Event Sourcing e tratamento de erros comuns a todos os módulos.
 * Mantenha este módulo pequeno — não coloque regra de negócio aqui.
 */
@ApplicationModule(displayName = "Shared Kernel", type = ApplicationModule.Type.OPEN)
package br.com.helpcsistemas.customeraccount.shared;

import org.springframework.modulith.ApplicationModule;
