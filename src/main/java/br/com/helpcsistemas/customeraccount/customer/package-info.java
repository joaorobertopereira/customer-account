/**
 * Módulo Customer: cadastro global da pessoa (identificada pelo CPF) compartilhado entre tenants.
 *
 * <ul>
 *   <li><b>Escrita</b>: agregado event-sourced {@code Customer} persistido no KurrentDB (stream {@code customer-<id>}),
 *       payload cifrado com a chave do titular.
 *   <li><b>Leitura</b>: read model no MongoDB, atualizado por projeção (subscription) — CQRS com consistência eventual.
 *   <li><b>Multi-tenant</b>: um tenant só enxerga/altera clientes vinculados a ele.
 * </ul>
 */
@ApplicationModule(
        displayName = "Customer",
        allowedDependencies = {"shared", "eventsourcing", "privacy"})
package br.com.helpcsistemas.customeraccount.customer;

import org.springframework.modulith.ApplicationModule;
