/**
 * Configurações do tenant e notificações para as aplicações tenant: webhook com assinatura HMAC, entrega
 * at-least-once com retentativas e proteção contra SSRF. A API pública ({@link
 * br.com.helpcsistemas.customeraccount.tenant.TenantNotifications}) é usada por outros módulos para notificar tenants.
 */
@ApplicationModule(
        displayName = "Tenant",
        allowedDependencies = {"shared", "privacy"})
package br.com.helpcsistemas.customeraccount.tenant;

import org.springframework.modulith.ApplicationModule;
