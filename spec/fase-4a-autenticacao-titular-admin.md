# Fase 4a — Autenticação do titular (passwordless + 2FA) e MFA dos admins de tenant

> Pré-leitura: [00-contexto-atual.md](00-contexto-atual.md). Independente; o step-up (seção 5) usa a fase 3a se existir.

## 1. Objetivo

Titular entra **sem senha** e com 2 fatores no realm central; tenants autenticam o titular por OIDC; admins de
tenant são obrigados a usar segundo fator (o backend já exige `acr` em produção). Nenhuma senha armazenada.

## 2. Keycloak (versionado em `docker/keycloak/realm-customer-account.json`)

- **Fluxo browser do titular** (`passwordless-browser`): passkey/WebAuthn passwordless (resident key + user
  verification; *conditional UI* do KC 26) como fator principal. Recuperação: verificação de e-mail + cadastro de
  nova passkey. Tipo de credencial `password` desabilitado no fluxo.
- **Admins de tenant** (`tenant-*-admin`): fluxo com `Condition - Level Of Authentication`; `acr.loa.map`
  `{"1": 1, "2": 2}`; nível 2 exige WebAuthn ou TOTP. Desativar `directAccessGrantsEnabled` no realm de produção
  (manter só no de dev). Produção: `TENANT_ADMIN_ACR_VALUES=2` (já suportado pelo backend).
- **Clients de titular por tenant** (`tenant-app-<x>-web`): público, Authorization Code + PKCE, mappers
  `tenant_id` (hardcoded), `customer_id` (atributo do usuário), audience `customer-account`, escopos `me:read`, `me:write`.
- Provisionamento do usuário: ao cadastrar o cliente, **não** criar usuário automaticamente; o titular se registra
  (self-registration com verificação de e-mail) e é vinculado por e-mail verificado + confirmação (OTP do e-mail
  cadastrado) → atributo `customer_id`. Admin API do Keycloak via service account com papel mínimo (`manage-users`).
- Bloqueio entre tenants: o backend só aceita token de titular se o `customer_id` estiver vinculado ao `tenant_id`
  do client (`CustomerDirectory`); opcionalmente SPI de autenticação no KC para negar o login cedo.

## 3. Backend

- `platform.security`: reconhecer token de titular (claim `customer_id`) → `SubjectPrincipal`; escopos `me:*`.
- Novas rotas `/api/v1/me/**` (perfil próprio, minhas validações, meus vínculos) — só leitura/edição do próprio
  cadastro; base para o portal da fase 5.
- Auditoria `audit.action=subject-login-linked`, `subject.self-read`.

## 4. Admins de tenant

- Usuários admin em grupo por tenant; papel `tenant-admin` só no client do próprio tenant (já é assim).
- Teste: token sem `acr=2` → 403 em `/api/v1/tenant/**` com `app.security.tenant-admin-acr-values=2`.

## 5. Step-up biométrico (opcional, usa fase 3a)

Anotação `@RequiresRecentIdentityVerification(maxAge = "P30D")` para operações sensíveis (ex.: troca de e-mail e
celular): exige `customer_verification_status[IDENTITY] = VALID` e concluída há menos de `maxAge`; senão
`BusinessRuleException IDENTITY_VERIFICATION_REQUIRED` (o front inicia o fluxo de link da 3a).

## 6. Tarefas

- [ ] T1 Realm: fluxos passwordless/LoA, clients `-web`, mappers, scopes `me:*`, realm de dev x produção (dois arquivos).
- [ ] T2 Vínculo usuário ↔ cliente (endpoint `POST /api/v1/me/link` com OTP do e-mail cadastrado) + service account.
- [ ] T3 Principal do titular, rotas `/api/v1/me/**`, checagem de vínculo com o tenant.
- [ ] T4 Step-up (opcional).
- [ ] T5 Testes: unit do conversor/principal; ITs com JWT mock (titular vinculado, não vinculado, outro tenant, `acr`); validação manual no compose com passkey (documentar passo a passo).
- [ ] T6 README seção 6 + `http/api.http`.

## 7. Pronto quando

Login sem senha funcionando no compose (passkey do navegador), admin sem segundo fator bloqueado com
`acr` exigido, `mvn clean verify` verde.
