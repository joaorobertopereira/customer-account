# Fase 3b — Canal de status para o front (SignalR + polling)

> Pré-leitura: [00-contexto-atual.md](00-contexto-atual.md). **Depende da fase 3a** (link e `AWAITING_HOLDER`).

## 1. Objetivo (D8)

Ao receber o link, o front abre um canal **SignalR** e inicia **polling** como fallback; quando o resultado da
empresa é processado, o backend avisa o front em tempo real. O front então consulta os detalhes pelo BFF.

## 2. Premissas e decisão técnica pendente

- O front **não tem token** do backend (chama via BFF com `client_credentials`). Logo, o canal usa um **token de
  canal** curto, opaco e limitado a uma validação.
- **SignalR não tem servidor oficial em Java.** Opções:

| Opção | Como | Quando |
|---|---|---|
| **A – Azure SignalR Service (serverless)** — recomendada se houver Azure | Backend expõe `negotiate` (devolve URL do serviço + JWT assinado com a AccessKey) e publica via REST `POST {endpoint}/api/v1/hubs/{hub}/users/{userId}`. Front usa `@microsoft/signalr` | Produção em Azure |
| B – SSE nativo (Spring MVC `SseEmitter`) | `GET .../stream` com o token de canal | Dev/compose e ambientes sem Azure |
| C – Hub SignalR em .NET (sidecar) | Serviço .NET mínimo recebendo eventos do backend | Só se A não for possível e o front exigir SignalR |

Implementar a porta `VerificationStatusPushPort` com adapters **A** e **B** (seleção por
`app.verification.status-push.provider = azure-signalr | sse | none`). Dev local: SSE (o emulador do Azure SignalR é
ferramenta .NET, sem imagem oficial). **Confirmar com o time de front/infra antes de implementar A.**

## 3. Contrato

Resposta do `POST /api/v1/verifications` (IDENTITY) passa a incluir:

```json
"statusChannel": {
  "token": "vsc_…",                 // 32 bytes aleatórios base64url; guardado só como HMAC
  "expiresAt": "…",                 // = link.expiresAt + 15 min
  "pollUrl": "/api/v1/public/verification-status",
  "signalr": { "negotiateUrl": "/api/v1/public/verification-hub/negotiate", "hub": "verification" },
  "streamUrl": "/api/v1/public/verification-status/stream"
}
```

Endpoints **públicos** (fora do JWT; autenticados pelo header `X-Verification-Status-Token`):

| Método | Caminho | Resposta |
|---|---|---|
| `GET` | `/api/v1/public/verification-status` | `{status: AWAITING_HOLDER|COMPLETED|EXPIRED, result: APPROVED|NOT_APPROVED|IN_REVIEW|null, updatedAt}` |
| `POST` | `/api/v1/public/verification-hub/negotiate` | `{url, accessToken}` (opção A; `userId` = id do canal) |
| `GET` | `/api/v1/public/verification-status/stream` | SSE `event: verification-status` (opção B) |

Mensagem push (ambas as opções): alvo `verificationStatusChanged` com o mesmo corpo do polling.

**Não revelar fraude ao titular**: o canal público mapeia `APPROVED → APPROVED`, `MANUAL_REVIEW → IN_REVIEW`,
`REJECTED | FRAUD_SUSPECTED | ERROR → NOT_APPROVED`. Detalhes (`outcome`, `reasonCodes`, score) só pela API do
tenant (`GET /api/v1/verifications/{id}` via BFF).

## 4. Implementação

- `StatusChannelRegistryPort` (Mongo `verification_status_channels`: hash do token → verificationId, tenantId,
  expiresAt; índice TTL). Reusar o canal quando o link for reutilizado (D5).
- Disparo do push: subscription **live** por instância (sem checkpoint, a partir do fim do `$all`) para eventos de
  conclusão/expiração → `VerificationStatusPushPort.publish(channelId, status)`. Com Azure, uma instância basta
  (fan-out no serviço); com SSE, cada instância avisa só as conexões que mantém.
- Polling lê o read model (`verification_views`) — barato e eventualmente consistente.
- CORS: origens permitidas por tenant (`allowedOrigins` em configuração do tenant, admin API em
  `/api/v1/tenant/front-origins`) aplicadas só às rotas `/api/v1/public/**`.
- Rate limit nas rotas públicas por token e por IP (ver T2 em [fase-t-transversal.md](fase-t-transversal.md);
  mínimo nesta fase: limite simples em memória por token).
- Config: `app.verification.status-push.{provider, azure.{connection-string (Vault), hub}}`, `poll-interval-hint` (5s).

## 5. Sequência no front (para documentar ao time de front)

1. BFF chama `POST /api/v1/verifications` → recebe `link` + `statusChannel`.
2. Front conecta SignalR (`negotiateUrl` com o header do token) **e** inicia polling a cada 5 s (backoff até 30 s).
3. Abre o link (nova aba/redirect). Ao voltar pelo `returnUrl`, continua ouvindo.
4. Mensagem `verificationStatusChanged` ou polling com `status != AWAITING_HOLDER` → para o polling, fecha o canal
   e pede os detalhes ao BFF.

## 6. Tarefas

- [ ] T1 Token de canal (geração, hash, TTL, reuso) + campo `statusChannel` na resposta.
- [ ] T2 Endpoint de polling público + mapeamento de resultado sem revelar fraude + `SecurityConfig` + CORS por tenant.
- [ ] T3 Porta de push + adapter SSE + subscription live.
- [ ] T4 Adapter Azure SignalR (negotiate + REST publish, JWT com AccessKey) — após confirmação da infra.
- [ ] T5 Rate limit básico nas rotas públicas.
- [ ] T6 ITs: polling antes/depois do callback; token inválido/expirado → 401; token de outra validação não vê nada; SSE recebe o evento; mapeamento `FRAUD_SUSPECTED → NOT_APPROVED`. Unit test do JWT de negotiate.
- [ ] T7 README + `http/api.http` + guia curto para o front (seção 5).

## 7. Pronto quando

`mvn clean verify` verde; no compose, o fluxo `simulated-hosted` atualiza o front via SSE e via polling; adapter
Azure validado contra uma instância real (ou documentado como pendente se a infra não existir).
