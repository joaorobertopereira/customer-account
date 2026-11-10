# Fase T — Itens transversais (independentes entre si)

> Pré-leitura: [00-contexto-atual.md](00-contexto-atual.md). Cada item pode ser feito isoladamente.

| Id | Item | Especificação resumida | Pronto quando |
|---|---|---|---|
| T1 | `Idempotency-Key` nos POSTs | Header opcional; Mongo `idempotency_keys` (tenant+key → hash do corpo + resposta, TTL 24h); mesma chave e corpo diferente → 422 | IT de repetição devolve a mesma resposta |
| T2 | Rate limiting | Bucket4j por tenant (`azp`) e por rota; rotas públicas por IP/token; 429 com `Retry-After`; estado distribuído (Mongo ou Redis — decidir) | Teste de carga curto mostra 429 |
| T3 | Parking de eventos com falha | Subscriber com N falhas no mesmo evento → grava em `eventstore_parked_events`, avança o checkpoint, alerta; API admin (role `admin`) para reprocessar | Evento envenenado não trava a projeção. **Implementado**: `app.eventstore.max-processing-attempts` (5), `ParkedEvents` e `/api/v1/admin/parked-events` (listar, reprocessar, descartar) |
| T4 | Snapshots | `Customer`, `ConsentLedger`, `VerificationProcess` e `TenantVerificationPolicy` com snapshot a cada 10 eventos no MongoDB (`eventstore_snapshots`, um documento por stream), cifrado com a chave do titular quando há dados pessoais | Rehydrate lê snapshot + cauda. **Implementado** (`AggregateSnapshots`) |
| T5 | Upcasters | Registro de upcasters por `Tipo.vN` no codec (base para mudanças de payload) | Testes com eventos v1 antigos |
| T6 | Rotação de KEK | `vault write -f transit/keys/customer-account-kek/rotate` + job de rewrap das DEKs (`transit/rewrap`), `min_decryption_version` | Rewrap idempotente testado |
| T7 | Auditoria imutável | Logs `audit.*` também em store append-only (stream `audit-<data>` no KurrentDB ou bucket WORM) com hash encadeado | Verificação de integridade da cadeia |
| T8 | Dashboards e alertas | Grafana provisionado: latência/erros por provedor, failovers, callbacks rejeitados, fila de webhooks, lag das subscriptions | Dashboards no compose |
| T9 | Hardening de produção | KurrentDB TLS + usuário; Mongo TLS + usuário de menor privilégio; Vault AppRole/K8s auth em vez de token; realm sem password grant; headers de segurança; imagem distroless/non-root (já) com scan | Checklist revisado com segurança |
| T10 | Teste de carga | k6/Gatling: cadastro, consulta, pedido de link e callbacks com milhares de usuários simultâneos; metas p95 definidas com o negócio | Relatório e ajustes de pool/índices |
