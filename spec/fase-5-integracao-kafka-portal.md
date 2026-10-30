# Fase 5 — Kafka, eventos para tenants e portal do titular

> Pré-leitura: [00-contexto-atual.md](00-contexto-atual.md). Independente (o portal usa a 4a para autenticar o titular).

## 1. Kafka no compose

- `apache/kafka` (KRaft, 1 nó) + UI opcional (`kafbat/kafka-ui`); dev sem TLS, produção SASL/SCRAM + TLS + ACLs.
- Testcontainers `org.testcontainers.kafka.KafkaContainer` no `IntegrationTestSupport`.

## 2. Publicação de eventos de integração (outbox nativo)

- Módulo `integration` (deps: shared, eventsourcing, customer, verification, tenant, consent se existir).
- Subscription `kafka-publisher-v1` em `$all` → mapeia eventos de domínio para **eventos de integração públicos**
  (CloudEvents JSON, versionados, **sem dado pessoal**: ids, status, outcome, timestamps) → produz com producer
  idempotente (`acks=all`, `enable.idempotence=true`), chave = `customerId`, headers `traceparent`, `ce_id`
  determinístico. Checkpoint só após ack (at-least-once; consumidores deduplicam por `ce_id`).
- Tópicos por tenant `ca.tenant.<tenantId>.events.v1` com ACL por prefixo (isolamento) + tópico interno
  `ca.internal.events.v1`. Schemas JSON em `src/main/resources/schemas/` + testes de contrato.
- Eventos: `customer.registered|updated|erased|linked|unlinked`, `verification.completed|validity_expired`,
  `consent.granted|revoked`.

## 3. Comandos de tenants via Kafka (decisão anterior: tenants via REST + Kafka)

- Tópico `ca.tenant-commands.v1` (provisionar/atualizar tenant: webhook, origens, finalidades) consumido com
  validação de schema, idempotência por `commandId` e DLQ `ca.tenant-commands.dlq`. Mesmo caso de uso das APIs REST.

## 4. Portal do titular (backend; LGPD art. 18)

Rotas `/api/v1/me/requests` (token do titular, fase 4a). Agregado `DataSubjectRequest` com prazo de 15 dias (art. 19):

| Direito | Implementação |
|---|---|
| Confirmação e acesso | Export JSON do cadastro, validações (sem bruto do provedor), consentimentos, vínculos |
| Correção | Reusa comandos do `customer` com auditoria `requested-by=subject` |
| Portabilidade | Export JSON estruturado assinado (download com link de uso único) |
| Compartilhamento | Lista de tenants vinculados e empresas de validação usadas (dos eventos `sharedFields`) |
| Revogação de consentimento | Delegada ao módulo `consent` |
| Eliminação | Fluxo atual (crypto-shredding), respeitando retenções legais (decisão em aberto) |

## 5. Tarefas

- [ ] T1 Kafka no compose + Testcontainers + config (`spring.kafka.*`).
- [ ] T2 Módulo `integration`: mapeadores, publisher com checkpoint pós-ack, tópicos por tenant, schemas + testes de contrato.
- [ ] T3 Consumidor de comandos de tenant + DLQ.
- [ ] T4 `DataSubjectRequest` + rotas `/api/v1/me/requests` + export.
- [ ] T5 ITs: evento publicado com trace e sem PII; reprocessamento não duplica `ce_id`; tenant A não recebe evento de B; comando inválido vai para DLQ; pedido de acesso gera export completo.
- [ ] T6 README (arquitetura, roadmap), `http/api.http`, dashboards de lag no Grafana.

## 6. Pronto quando

Eventos chegam ao tópico do tenant certo, sem PII, com trace ponta a ponta; portal atende acesso/portabilidade/
eliminação; `mvn clean verify` verde.
