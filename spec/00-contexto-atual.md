# Contexto atual do código (leia antes de qualquer fase)

Resumo para retomar o trabalho sem reler o projeto inteiro.

## Stack e build

- Java 25 (virtual threads), Spring Boot 4.1.2, Spring Modulith 2.1.2, Jackson 3 (`tools.jackson.*`; anotações
  continuam `com.fasterxml.jackson.annotation`), springdoc 3.2.0, kurrentdb-client 1.2.1, resilience4j-spring-boot4
  2.4.0, Testcontainers 2, ArchUnit 1.5.1, Spotless (palantir), JaCoCo.
- JDK: `C:\dev\.jdk\jdk-25.0.5.0.3` (o `java` do PATH é 21). Antes do Maven:
  `$env:JAVA_HOME='C:\dev\.jdk\jdk-25.0.5.0.3'; $env:Path="$env:JAVA_HOME\bin;$env:Path"`.
- Fluxo: `mvn spotless:apply` → `mvn verify` (unit + ArchUnit + Modulith + ITs com Docker + JaCoCo + Spotless).
  No PowerShell o exit code 1 pode ser falso negativo: confira `BUILD SUCCESS`. Após renomear enums/classes use
  `mvn clean verify` (classes de teste antigas mascaram erros).
- Docker: usar `kurrentplatform/kurrentdb:25.1` (docker.kurrent.io e CDN do quay falham na rede corporativa).
- Pacote raiz: `br.com.helpcsistemas.customeraccount`.

## Módulos (Spring Modulith; dependências declaradas em `package-info.java` → `allowedDependencies`)

| Módulo | Papel | API pública relevante |
|---|---|---|
| `shared` (OPEN) | `EventSourcedAggregate`, `TenantId`, `Actor`, exceções de domínio, Problem Details | — |
| `platform` | Segurança (resource server, escopos, `TenantId`/`Actor` resolvidos do token), `FlowLoggingAspect`, OTel, OpenAPI, `Clock`, `@EnableScheduling` | — |
| `eventsourcing` | `EventStore`, `NewEvent`, `StoredEvent`, `ExpectedRevision`, `EventStreamSubscriber` (catch-up em `$all` com checkpoint no Mongo, `traceparent` nos metadados) | todas |
| `privacy` | `PersonalDataCipher` (AES-GCM por titular, DEK embrulhada no Vault Transit), `BlindIndex` (HMAC), `CryptoShredder` | todas |
| `secrets` | `SecretStore.read(path)` → Vault KV v2 (`secret/customer-account/<path>`) com cache; `local` em dev/test | `SecretStore` |
| `customer` | Agregado `Customer` (ES), reserva de CPF, read model `customer_views`, eliminação | `CustomerDirectory`, `CustomerSnapshot`, `CustomerErasureCompleted` |
| `verification` | Política por tenant; processo síncrono (OTP) e hospedado (link/callback) event-sourced; `ResilientProviderGateway`; read model/status atual, inbox e incidente | — |
| `tenant` | Webhook do tenant (`tenant_webhooks`), fila `tenant_notifications`, assinatura `v1=HMAC`, guarda SSRF | `TenantNotifications`, `TenantNotification`, `TenantNotificationDelivered/Failed` |

## Arquitetura hexagonal por módulo (verificada por `HexagonalArchitectureTests`)

```
<modulo>/domain/model, domain/event     Java puro (sem Spring/Jackson/Mongo/Kurrent)
<modulo>/application/port/in|out        casos de uso e portas
<modulo>/application/service            orquestração (sem adapter, sem eventsourcing/privacy/spring-data/kurrent)
<modulo>/application/query              views de leitura
<modulo>/adapter/in/web|subscription|scheduler|event
<modulo>/adapter/out/...                persistência, provedores, http
<modulo>/adapter/codec                  evento de domínio ⇄ payload versionado (v1) + cifra
<modulo>/config                         @ConfigurationProperties fora de adapter quando usado por várias camadas
```

Regras: `adapter.in` não depende de `adapter.out`; só `eventsourcing` usa `io.kurrent`; controllers ficam em
`adapter.in.web`. O `FlowLoggingAspect` faz proxy de beans em `application`/`adapter` (métodos interceptados devem
ser `public`; `@ConfigurationProperties` são excluídos).

## Padrões já estabelecidos (reutilizar)

- **Agregado ES**: `raise(evento)` + `apply(evento)` com `switch` sobre `sealed interface`; `rehydrate(history, version)`.
  Repositório salva `pendingEvents()` com `ExpectedRevision`; conflito → `ConcurrencyConflictException`.
- **Codec**: tipo do evento `Nome.v1`; payload com dado pessoal → cifrado com `PersonalDataCipher` usando o
  `customerId` como subject (crypto-shredding). Eventos técnicos sem PII podem ser JSON em claro.
- **Reserva/unicidade**: stream de lock com `ExpectedRevision.noStream()` (ex.: `customer_cpf-<HMAC>`).
- **Projeção**: `EventStreamSubscriber` com nome `<algo>-v1`, idempotente por revisão; consultas só no read model.
- **Provedor de validação síncrono**: implementar `verification.adapter.out.provider.VerificationProvider`
  (`id`, `supportedTypes`, `verify`, `confirm`); credenciais via `ProviderCredentialsResolver`
  (`providers/<id>/tenants/<tenant>` → `providers/<id>/default`); HTTP via `ProviderHttpClients.create`.
  O gateway aplica retry + circuit breaker por provedor e converte falhas em `ProviderUnavailableException`
  (aciona contingência).
- **Validação hospedada**: `HostedVerificationProvider.start` retorna `HostedSession`; `HostedCallbackProvider`
  autentica/normaliza callback. `GenericHostedProvider` lê `app.verification.hosted-providers.*` (placeholders
  desabilitados para Legitimuz/Netrin); `SimulatedHostedProvider` exercita o fluxo no compose. Reuso/correlação em
  `HostedSessionRegistryPort`; callback autenticado vai à inbox Mongo e é processado depois.
- **Resultado padronizado**: `ProviderResult(decision, score, reasonCodes, transactionId, latencyMs, rawResponse)`;
  `ProviderDecision` = `APPROVED|REJECTED|INCONCLUSIVE|CHALLENGE_SENT`.
- **Processo**: `VerificationStatus` = `PENDING|AWAITING_CONFIRMATION|AWAITING_HOLDER|COMPLETED`;
  `VerificationOutcome` = `APPROVED|REJECTED|MANUAL_REVIEW|FRAUD_SUSPECTED|ERROR|FAILED|EXPIRED`. Expiração por
  `VerificationExpirationScheduler` (`findAwaitingExpiredBefore`). Conclusão notifica o tenant via
  `VerificationResultNotificationSubscriber` → `TenantNotifications` (webhook `verification.completed`).
- **Auditoria**: logs com `audit.action`, `tenant.id`, `changed.by`; nunca logar argumentos/PII.
- **Erros**: `BusinessRuleException` (422), `InvalidValueException` (400), `ResourceNotFoundException` (404 — também
  para recurso de outro tenant), `ConcurrencyConflictException` (409). Problem Details com `code`.

## Segurança (platform/security/SecurityConfig)

- Resource server JWT (Keycloak realm `customer-account`, `aud=customer-account`). `tenant_id` sempre do token.
- Escopos: `customer:read|write|erase`, `verification:read|write`. Admin do tenant: role de client `tenant-admin`
  (só roles de `resource_access[azp]`), `/api/v1/tenant/**`; `app.security.tenant-admin-acr-values` exige `acr`.
- Rotas públicas hoje: health/info, OpenAPI. Qualquer rota pública nova precisa de autenticação própria
  (assinatura/tokens) e entrar explicitamente no `SecurityConfig`.

## Testes

- `IntegrationTestSupport`: containers compartilhados (Mongo replica set, KurrentDB, Mailpit), perfil `test`,
  helpers `tenant(tenantId, scopes...)` e `tenantAdmin(tenantId)` (JWT mock), `register(token, cpf)`.
- Use CPFs válidos e **diferentes** por teste (contexto e containers são compartilhados entre classes).
- Provedores HTTP: `MockRestServiceServer.bindTo(RestClient.builder())`; variáveis de URI chegam codificadas.
- Provedores de sandbox: `simulated-approve|reject|inconclusive|unavailable` (`app.verification.simulated-providers-enabled`).

## Infra (docker-compose.yml)

`app`, `kurrentdb` (2113), `mongodb` (27017), `vault` + `vault-init` (Transit + KV `secret/`, token da app em
volume), `keycloak` (`KEYCLOAK_PORT`, padrão 8180), `mailpit` (8025/1025), `lgtm` (Grafana 3000, OTLP 4317/4318).

## Arquivos de referência

- Domínio de validação: `verification/domain/model/VerificationProcess.java`, `TenantVerificationPolicy.java`
- Serviço: `verification/application/service/VerificationService.java`, `VerificationLifecycleService.java`
- Gateway: `verification/adapter/out/provider/ResilientProviderGateway.java`
- Codec: `verification/adapter/codec/VerificationEventCodec.java`
- Webhook de saída: `tenant/application/service/TenantNotificationService.java`
- Requisições prontas: `http/api.http`; documentação: `README.md`
