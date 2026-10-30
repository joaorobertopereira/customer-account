# Fase 4b — Consentimento e vínculo entre tenants

> Pré-leitura: [00-contexto-atual.md](00-contexto-atual.md). Independente (a confirmação pelo titular fica melhor com a 4a).

## 1. Objetivo

Registrar consentimentos por finalidade (LGPD arts. 7º, 8º, 9º), exigir aceite do titular para que um **novo
tenant** acesse um cadastro que já existe, permitir **desvincular** um tenant sem apagar a pessoa e expor a cada
tenant **só os campos necessários** (minimização).

## 2. Módulo `consent` (novo; deps: shared, eventsourcing, privacy, customer)

- Catálogo de finalidades por tenant (admin): `PUT /api/v1/tenant/purposes/{code}`
  `{description, legalBasis: CONSENT|CONTRACT|LEGAL_OBLIGATION|LEGITIMATE_INTEREST|CREDIT_PROTECTION, fields[], policyVersion}`.
- Agregado `ConsentLedger` por (cliente, tenant) — stream `consent-<customerId>-<tenantId>`:
  `ConsentGranted(purpose, policyVersion, channel, evidence, grantedAt)`, `ConsentRevoked(purpose, reason, at)`.
  Evidência: canal (app, web, presencial), id da sessão, hash do texto apresentado. Cifrado com a chave do titular.
- API do tenant: `POST /api/v1/customers/{id}/consents`, `DELETE .../consents/{purpose}`, `GET .../consents`.
- Projeção `consent_views` e API pública do módulo `ConsentDirectory.allowedFields(tenant, customer)`.
- Webhook ao tenant `consent.revoked`.

## 3. Vínculo de cliente existente a novo tenant

Hoje o cadastro com CPF existente vincula direto (`CustomerLinkedToTenant`). Passa a ser:

1. `POST /api/v1/customers` com CPF existente → `CustomerLinkRequested(tenant)` → **202** `{status: LINK_PENDING}`
   sem devolver nenhum dado do cadastro existente (nem confirmar existência além do status).
2. Titular confirma por OTP no e-mail/celular **já cadastrado** (reuso de `own-email-otp`) ou pelo login da 4a →
   `CustomerLinkApproved` + `CustomerLinkedToTenant`. Recusa/expiração (7 dias) → `CustomerLinkRejected`.
3. Dados informados pelo novo tenant que diferem do cadastro viram **sugestão de atualização** (não sobrescrevem).

Desvínculo: `DELETE /api/v1/customers/{id}/link` (tenant) ou pelo titular → `CustomerUnlinkedFromTenant`; o tenant
perde acesso e suas validações/consentimentos são removidos do read model; a pessoa continua para os demais.

## 4. Minimização por escopo/finalidade

- Escopos de leitura granulares: `customer:read:identity` (nome, CPF, nascimento), `customer:read:contact`,
  `customer:read:address`, `customer:read:full`. `customer:read` atual = `full` (compatibilidade, depreciar).
- Resposta filtrada por **escopo do token ∩ campos das finalidades consentidas/bases legais** do tenant.
- Auditoria de leitura registra quais campos foram expostos.

## 5. Tarefas

- [ ] T1 Módulo `consent`: domínio, codec, repositório, projeção, APIs, testes.
- [ ] T2 Fluxo de vínculo com aprovação do titular (customer + verification OTP) e desvínculo.
- [ ] T3 Filtro de campos por escopo/finalidade no `CustomerQueryService` + mapper.
- [ ] T4 Webhooks `consent.revoked`, `customer.link_approved`, `customer.unlinked`.
- [ ] T5 ITs: vínculo pendente não vaza dados; aprovação libera; desvínculo remove acesso; revogação de consentimento reduz campos; isolamento.
- [ ] T6 README seções 2 e 5, ModularityTests (novo módulo), `http/api.http`.

## 6. Pronto quando

Novo tenant não acessa cadastro existente sem aceite do titular; leitura respeita escopo e finalidade;
`mvn clean verify` verde.
