# Especificações das fases pendentes — customer-account

Registro de tudo que falta implementar. Cada fase é **autocontida**: pode ser implementada numa sessão separada
lendo apenas [00-contexto-atual.md](00-contexto-atual.md) + o arquivo da fase.

## Estado

| Fase | Status |
|---|---|
| 1 – Cadastro ES/CQRS, privacidade, Keycloak, OTel, compose | ✅ concluída |
| 2a – Política de provedores por tenant, processo de validação, contingência | ✅ concluída |
| 2b – Segredos (Vault KV), OTP próprio, Twilio, ZeroBounce, webhook assinado ao tenant | ✅ concluída |

## Pendentes

| Spec | Fase | Depende de | Resumo |
|---|---|---|---|
| [fase-3a-validacao-hospedada.md](fase-3a-validacao-hospedada.md) | 3a | — | Link de validação (Legitimuz/Netrin genéricos), webhook de entrada, regras de resultado, validade/revalidação |
| [fase-3b-canal-tempo-real.md](fase-3b-canal-tempo-real.md) | 3b | 3a | Token de canal para o front, polling público e push em tempo real (SignalR/SSE) |
| [fase-4a-autenticacao-titular-admin.md](fase-4a-autenticacao-titular-admin.md) | 4a | — | Passwordless + 2FA do titular, MFA/step-up dos admins de tenant no Keycloak |
| [fase-4b-consentimento.md](fase-4b-consentimento.md) | 4b | — (usa 3a opcionalmente) | Módulo `consent`, vínculo de cliente a novo tenant com consentimento, minimização por escopo |
| [fase-5-integracao-kafka-portal.md](fase-5-integracao-kafka-portal.md) | 5 | — | Kafka no compose, publicação de eventos para tenants, portal do titular (art. 18) |
| [fase-t-transversal.md](fase-t-transversal.md) | T | — | Idempotência, rate limit, DLQ, snapshots, upcasters, rotação de KEK, auditoria imutável |

Ordem sugerida: **3a → 3b → 4a → 4b → 5**, com itens de **T** intercalados conforme necessidade.

## Decisões registradas (2026-10-30)

| # | Decisão |
|---|---|
| D1 | Validação de dados pessoais, documento e biometria fica **somente com Legitimuz e Netrin**, implementadas de forma **genérica** (sem contrato real) até termos a documentação de cada uma. Serpro Datavalid, Unico, Serasa, Kickbox e a antiga fase 2c **saem do escopo**. Permanecem as integrações de contato já entregues na 2b: OTP próprio por e-mail, Twilio Verify (OTP SMS), Twilio Lookup e ZeroBounce. |
| D2 | Fluxo é **hospedado e assíncrono**: o backend devolve o **link de validação** da empresa; o titular conclui no site da empresa; o resultado chega por **webhook da empresa** e é registrado como evento. |
| D3 | Quem pede o link é o **frontend via BFF do tenant** (token `client_credentials`, escopo `verification:write`). A orquestração da jornada é do frontend. |
| D4 | Falha técnica ao gerar o link → **contingência automática** para a empresa reserva, com falha registrada no tenant (mesmo comportamento da fase 2a). |
| D5 | Pedido de novo link com outro **pendente e não expirado** → devolve **o mesmo link**. |
| D6 | Regras que classificam o resultado (sucesso / fraude / erro) são da **política do tenant** (administrador). |
| D7 | Validação aprovada tem **prazo de validade configurável pelo tenant**; o titular pode revalidar várias vezes (histórico completo). |
| D8 | O front acompanha o resultado por **canal em tempo real (SignalR)** aberto ao receber o link, **mais polling** como fallback. |

## Pendências fora do código (dependem do negócio)

- Documentação/sandbox da **Legitimuz** e da **Netrin**: criação de sessão, formato do link, expiração, webhook
  (assinatura, IPs, retentativas), catálogo de status e motivos de fraude. Ao receber, criar os adapters reais
  conforme a seção "Adapter real" da fase 3a.
- Confirmar infraestrutura do **SignalR** (Azure SignalR Service ou alternativa) — ver fase 3b.
- Itens da seção 10 do README (retenção x eliminação, eliminação por tenant, BR-only, key store separado).
