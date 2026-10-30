/**
 * Validações de identidade e contato (biometria, prova de vida, documento, antifraude, e-mail, celular).
 *
 * <ul>
 *   <li>{@code TenantVerificationPolicy}: cada tenant escolhe o provedor (e a contingência) por tipo de validação;
 *       alterações são eventos auditáveis e valem a partir da próxima validação.
 *   <li>{@code VerificationProcess}: todo o ciclo (solicitação, chamadas, falhas, contingência, retorno do provedor,
 *       veredito) é registrado como eventos; o tenant recebe um resultado padronizado.
 * </ul>
 */
@ApplicationModule(
        displayName = "Verification",
        allowedDependencies = {"shared", "eventsourcing", "privacy", "secrets", "customer", "tenant"})
package br.com.helpcsistemas.customeraccount.verification;

import org.springframework.modulith.ApplicationModule;
