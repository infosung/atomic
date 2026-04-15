# Usage Overview

> Status: current pre-1.0 usage guide for the active mainline branch.

## Before You Start

- Release-validation baseline: Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.5`
- Other runtime combinations are possible, but they are not part of the current release-line
  guarantee unless your team validates them in its own CI before rollout.
- Use `atomic.starter` to enable conditional auto-configuration.
- Add `atomic.contract` when your app directly uses `BaseResponse` / `HttpStatusException`.
- Add only the feature modules you use.

Recommended entry docs:
- Start with minimal setup: [Atomic Quick Start](quick-start.md)
- Move to production criteria: [Advanced Operations Playbook](advanced-playbook.md)
- Current release summary: [Release Notes: `v0.1.3`](../release-notes/v0.1.3.md)
- Current patch upgrade: [Release Migration Guide: `v0.1.2` -> `v0.1.3`](../migration/v0.1.2-to-v0.1.3.md)
- First `0.1.x` adoption: [Release Migration Guide: `v0.0.5` -> `v0.1.1`](../migration/v0.0.5-to-v0.1.1.md)

## What Atomic Solves

Atomic is a module set for Kotlin/Spring services that want consistent:

- API response shape and shared DTO/util contracts
- storage integration contracts and implementations
- web exception handling and API logging
- rate limiting and idempotent request control
- JWT authentication filter flow
- social OAuth provider integration

Use only the modules you need.

## 1. Select Modules by Use Case

- `atomic.contract`: shared response/header/exception/util model used by all layers
- `atomic.crypto`: Spring-free cryptographic primitives and key-rotation helpers
- `atomic.starter`: conditional Spring Boot auto-configuration entrypoint
- `atomic.storage`: storage module (S3-compatible backends such as S3/R2/MinIO, plus media helpers)
- `atomic.app.version`: narrow app-level version API module
- `atomic.app.storage.api`: narrow app-level image API module
- `atomic.app.oauth.redirect`: narrow app-level OAuth redirect/callback relay module
- `atomic.app`: convenience bundle module (`version` + `storage-api` + `oauth-redirect`)
- `atomic.spring.web`: API error handling, request/response logging, RestTemplate interceptor/error handler
- `atomic.spring.idempotency`: HTTP idempotency filter for one-time POST processing
- `atomic.spring.security`: JWT issue/verify + Spring Security filter integration
- `atomic.spring.oauth2`: OAuth provider integration (Google/Kakao/Apple), redirect flow and id token/userinfo identity resolution
- `atomic.heartbeat`: periodic heartbeat ping orchestration with optional DB/Redis dependency checks and dedup policy
- `atomic.event.log`: common event log ingestion core
- `atomic.event.log.parquet`: bounded Parquet export coordination and spool/store integration
- `atomic.event.log.iceberg`: Iceberg publication strategy and commit/table contracts
- `atomic.event.log.duckdb`: DuckDB query helper layer for event-log analysis
- `atomic.event.log.spring.web`: bridge from `atomic.spring.web` API logs into event-log envelopes
- `atomic.event.log.ingest.api`: official Spring MVC ingest API with async memory-queue intake

If your first goal is event-log collection rather than app APIs, start in this order:

1. `atomic.event.log`
2. `atomic.event.log.parquet`
3. `atomic.event.log.ingest.api` or `atomic.event.log.spring.web`
4. `atomic.event.log.iceberg` and `atomic.event.log.duckdb` only when you need them

Recommended event-log reading order:
- collector/server composition: [atomic.event.log Guide](atomic-event-log.md)
- client envelope and batching: [atomic.event.log Client Guide](atomic-event-log-client.md)
- export, Iceberg, and DuckDB: [atomic.event.log Lakehouse Guide](atomic-event-log-lakehouse.md)

## 2. Dependencies

Published artifact examples (`v0.1.3`):

```kotlin
dependencies {
  implementation("com.infosung:atomic.event.log:0.1.3")
  implementation("com.infosung:atomic.event.log.iceberg:0.1.3")
  implementation("com.infosung:atomic.event.log.parquet:0.1.3")
  implementation("com.infosung:atomic.event.log.duckdb:0.1.3")
  implementation("com.infosung:atomic.event.log.spring.web:0.1.3")
  implementation("com.infosung:atomic.event.log.ingest.api:0.1.3")
  implementation("com.infosung:atomic.contract:0.1.3")
  implementation("com.infosung:atomic.crypto:0.1.3")
  implementation("com.infosung:atomic.storage:0.1.3")
  implementation("com.infosung:atomic.spring.web:0.1.3")
  implementation("com.infosung:atomic.spring.security:0.1.3")
  implementation("com.infosung:atomic.spring.idempotency:0.1.3")
  implementation("com.infosung:atomic.spring.oauth2:0.1.3")
  implementation("com.infosung:atomic.heartbeat:0.1.3")
  implementation("com.infosung:atomic.starter:0.1.3")
  implementation("com.infosung:atomic.app.version:0.1.3")
  implementation("com.infosung:atomic.app.storage.api:0.1.3")
  implementation("com.infosung:atomic.app.oauth.redirect:0.1.3")
  implementation("com.infosung:atomic.app:0.1.3")
}
```

Current `.github/workflows/publish-maven-central.yml` publishes:

- `atomic-event-log`
- `atomic-event-log:iceberg`
- `atomic-event-log:parquet`
- `atomic-event-log:duckdb`
- `atomic-event-log:spring-web`
- `atomic-event-log:ingest-api`
- `atomic-contract`
- `atomic-crypto`
- `atomic-storage`
- `atomic-spring-web`
- `atomic-spring-security`
- `atomic-spring-idempotency`
- `atomic-spring-oauth2`
- `atomic-heartbeat`
- `atomic-starter`
- `atomic-app:app-version`
- `atomic-app:oauth-redirect`
- `atomic-app:storage-api`
- `atomic-app`

Local multi-module adoption is still available when you need source-level customization:

```kotlin
dependencies {
  implementation(project(":atomic-event-log"))
  implementation(project(":atomic-event-log:iceberg"))
  implementation(project(":atomic-event-log:parquet"))
  implementation(project(":atomic-event-log:duckdb"))
  implementation(project(":atomic-event-log:spring-web"))
  implementation(project(":atomic-event-log:ingest-api"))
  implementation(project(":atomic-starter"))
  implementation(project(":atomic-contract"))
  implementation(project(":atomic-crypto"))

  // add only modules you use
  implementation(project(":atomic-app"))
  implementation(project(":atomic-storage"))
  implementation(project(":atomic-spring-web"))
  implementation(project(":atomic-spring-idempotency"))
  implementation(project(":atomic-spring-security"))
  implementation(project(":atomic-spring-oauth2"))
  implementation(project(":atomic-heartbeat"))
}
```

## 3. Quick Start (First Day)

1. Add the narrowest module first. For app APIs, prefer `atomic.app.version`, `atomic.app.storage.api`, or `atomic.app.oauth.redirect`; use `atomic.app` only when you want the bundle on purpose.
2. Add `atomic.starter` when you need auto-configured infra. Add `atomic.contract` only when your app directly uses `BaseResponse` / `HttpStatusException`.
3. Add one feature module only (`app.version`, `app.storage.api`, `app.oauth.redirect`, `storage`, `web`, `idempotency`, `security`, `oauth2`, or `heartbeat`); for image/oauth redirect features, also add their prerequisite modules/beans from the module matrix below.
4. Register only minimum required beans from that guide.
5. Run one smoke endpoint.
6. Add optional features after baseline works.

## 4. Module Combination Matrix

| Goal | Modules | First Setup |
|---|---|---|
| Standard API response + shared contracts | `starter` + `contract` | Use `BaseResponse`, `HttpStatusException` |
| App-ready version API | `app.version` | enable `atomic.app.version.enabled`, provision `service_version` schema, seed version rows, and keep host customization on the exported `CheckAppVersionUseCase` bean |
| App-ready image upload/delete API | `app.storage.api` + `starter` + `storage` | enable `atomic.app.image.enabled`, provision `image` schema, and configure storage backends |
| App-ready OAuth redirect/callback relay | `app.oauth.redirect` + `starter` + `spring.oauth2` | enable `atomic.app.oauth.redirect.enabled`, consume `relayCode` in login API, configure store prerequisites (default `store.type=entity`), set non-empty `allowed-redirect-uri-prefixes`, and let your app issue its own session/token after relay consumption |
| Convenience bundle for multiple app APIs | `app` + any prerequisites still required by enabled features | enable only the specific `atomic.app.*.enabled` tracks you need |
| Object storage and media processing | `starter` + `storage` | set `atomic.storage.backends.*` and use `ImageService` |
| Exception response standardization | `starter` + `spring.web` (+ `contract` when app directly uses `BaseResponse` / `HttpStatusException`) | built-in scoped `AtomicHttpExceptionHandler` for `atomic.app.*`, or your `BaseExceptionHandler` subclass for broader/custom policy |
| API request/response audit logs | `starter` + `spring.web` | add `LogSaver` + `ApiLogAspect` implementation |
| API rate-limit filter | `starter` + `spring.web` | enable `atomic.web.rate-limit.enabled`, choose store (`auto/in-memory/redis/custom`), and review key policies (`path-key-strategy`, `missing-key-policy`, `ip.trust-forwarded-headers`) |
| HTTP idempotency (POST replay-safe) | `starter` + `spring.idempotency` | enable `atomic.idempotency.enabled`, configure key/ttl, and choose replay headers/body-cache limit |
| JWT auth for your API | `starter` + `spring.security` | set `atomic.security.jwt.*` and apply `JwtSecurityConfigurerAdapter` |
| Social login redirect flow | `starter` + `spring.oauth2` | set `atomic.oauth2.state.*` + `atomic.oauth2.providers.*`, and define one-time state store strategy (`in-memory-store.enabled=true` for single-node or custom/shared store) |
| Heartbeat ping + dependency checks | `starter` + `heartbeat` | enable `atomic.heartbeat.enabled`, set provider URL, optional DB/Redis checks and dedup mode |
| Full typical server stack | all modules | start with `starter` -> `storage/web` -> `security` -> `oauth2` |

## 5. App Module Responsibility Matrix

| Feature | Database schema | Shared store / external state | Host job / runbook | Security rule change | Error-envelope ownership |
|---|---|---|---|---|---|
| `atomic.app.version` | `service_version` table + policy rows | none | seed/update version policy data, including whether each row is already store-available | usually none beyond normal API rules | shared `atomic.spring.web` mapping for Atomic endpoints, or one host global exception policy |
| `atomic.app.storage.api` | `image` table | configured `storageClients` / backend bucket | yes; own the `DELETE_PENDING` recovery path by replaying the same DELETE, inspecting pending rows with `InspectDeletePendingImagesUseCase.inspectDeletePendingImages()`, or calling `RecoverDeletePendingImagesUseCase.recoverDeletePendingImages(limit)`; recovery batches claim rows to reduce overlap and reclaim stale claims after 15 minutes, but scheduler ownership remains with the host app | yes; include image paths in Spring Security matchers when security is enabled | shared `atomic.spring.web` mapping for Atomic endpoints, or one host global exception policy |
| `atomic.app.oauth.redirect` | default `entity` store needs `atomic_oauth_relay_code` table; `cache` / `in-memory` do not | yes; `OauthStateStore` for replay protection, plus relay store policy that matches deployment model | yes; own expired-row cleanup for entity relay store and decide local/dev vs multi-instance store policy | yes; allow redirect/callback endpoints and define CSRF policy for Apple `POST` callback | shared `atomic.spring.web` mapping for Atomic endpoints, or one host global exception policy |

Use this matrix as the shortest handoff summary for app-module adoption. If a row introduces
infrastructure your team does not want to own yet, stay on the narrower feature set.

OAuth redirect handoff summary:

- web: browser -> server callback -> allowlisted `https://...` frontend redirect with `relayCode`
- mobile: system browser or browser-based tab -> server callback -> allowlisted app link / deep link with `relayCode`
- desktop: system browser -> server callback -> allowlisted loopback URI or custom scheme with `relayCode`
- after that redirect, your app or frontend still has to send `relayCode` to its own login API and complete session/token issuance after `ConsumeOauthRelayCodeUseCase.consume(relayCode)`

## 6. Configuration Policy

- `atomic.starter` activates only when corresponding module classes are on classpath.
- `atomic.app` APIs are disabled by default and enabled by `atomic.app.version.enabled` / `atomic.app.image.enabled` / `atomic.app.oauth.redirect.enabled`.
- Some features still require application-specific beans (for example `ApiLogAspect`, `LogSaver`, or a custom `BaseExceptionHandler` when you want broader/custom exception policy than the built-in scoped Atomic handler).
- `atomic.app.version`, `atomic.app.image`, and `atomic.app.oauth.redirect` now depend on shared `atomic.spring.web` exception mapping rather than module-local advice beans.
- Host apps should keep one global exception policy and customize from public atomic exception types or stable `HttpStatusException.code`.
- Avoid message-text or internal FQCN matching for atomic app-module errors.
- Non-MVC platform filters now also prefer stable machine-readable codes for security, rate-limit, and idempotency rejection paths.
- OAuth provider beans from properties are registered when `OauthStateManager` is available and each provider `enabled=true` (auto path: `atomic.oauth2.state.enabled=true` + `atomic.oauth2.state.signing-secret`, or custom manager bean).
- `atomic.oauth2.state.signing-secret` must be at least 32 bytes; shorter values fail startup.
- OAuth one-time state consume requires store path (`in-memory-store.enabled=true` or custom/shared `OauthStateStore`).
- when `atomic.app.oauth.redirect.enabled=true`, `allowed-redirect-uri-prefixes` must be non-empty.
- when Spring Security is enabled, explicitly configure callback/redirect authorization and CSRF policy (especially Apple `POST` callback path).
- Rate-limit rules are evaluated in declaration order (first match wins), and reset/retry headers follow fixed-window boundary seconds.
- Rate-limit storage key is `actor|method|pathKey`; with default `path-key-strategy=rule-prefix`, unmatched routes share one `default` bucket.
- When both are enabled, run rate-limit before idempotency (starter defaults: rate-limit `-100`, idempotency `-50`).
- Heartbeat dedup `leader` mode automatically re-elects on lease expiry; choose backend (`redis`, `jdbc`, or `custom`) and tune renew/lease values.
- Start with minimum modules and add optional beans as needed.

## 7. Operational Checklist (All Modules)

- Keep secrets out of source code and inject via environment.
- Fix timezone/clock policy once and use consistently.
- Define one response format policy for all controllers.
- Verify error mapping behavior in non-200 scenarios.
- If you override app-module exception mapping, verify that your advice precedence still preserves the documented wire contract.
- Add integration tests for auth and callback flows before release.

## 8. Troubleshooting Index

- Response shape mismatch: check `BaseResponse` usage consistency.
- Storage upload `Unknown storageType`: check storage client/profile map keys and call-site `storageType` value.
- Image delete `stored storageType is unavailable for image delete`: check whether configured storage client keys still match persisted `image.storage_type` values.
- App image API setup may fail startup (not only API skip): check `atomic.app.image.enabled=true` with `ImageService`/`storageClients` bean availability.
- Image upload returned no thumbnail fields: check `atomic.app.image.thumbnail-enabled` and the request-level `thumbnailEnabled` override.
- Image delete rows remain with `DELETE_PENDING`: a previous storage delete failed; this is retryable cleanup work, not a completed delete. Keep the stored `storageType` mapping available, inspect backlog with `InspectDeletePendingImagesUseCase.inspectDeletePendingImages()`, retry the same DELETE after storage/backend recovery, or invoke `RecoverDeletePendingImagesUseCase.recoverDeletePendingImages(limit)` from an admin job. Recovery batches now claim eligible rows before cleanup to reduce duplicate retries across overlapping triggers.
- App oauth redirect setup now fails fast when enabled but required prerequisites are missing; check selected relay store dependencies, `OauthServiceProvider`, and replay-protected `OauthStateManager` together.
- Mobile/custom-scheme redirect URIs are supported, but allowlist matching still uses `scheme + host + port + path-prefix`.
  - `myapp://oauth/...` and `myapp:/oauth/...` are not interchangeable; configure the exact URI shape emitted by the client.
- OAuth callback errors: check state and redirect URI mapping, effective callback-binding mode (`strict`, `relaxed`, `disabled`), and callback-binding failure type (`state is missing`, `cookie is missing`, `token mismatch`, `cookie is ambiguous`).
- OAuth relay cache store startup failure: check `atomic.app.oauth.redirect.store.cache.cache-name` exists in `CacheManager` and that the selected backend supports atomic remove-and-return consume (with default `store.fail-fast=true`).
- OAuth redirect startup summary now logs configured relay store, fail-fast, callback-binding mode, and replay protection shape together. If the warning mentions `process-local per instance`, treat the current relay/state configuration as local-only or intentionally single-node.
- OAuth redirect prefix configuration issue: empty `allowed-redirect-uri-prefixes` fails startup; invalid prefix entries also fail startup before traffic begins.
- JWT unauthorized unexpectedly: check channel resolver and token source.
- API logs missing: check `ServiceLogger.send()` scheduling.
- Heartbeat fail flood or silence: verify `atomic.heartbeat.dedup.mode`, leader backend bean availability, and ping/check intervals.
- Artifact not found: switch to `project(":...")` dependency until repository resolution is ready.

## 9. Recommended Reading Order

1. [Atomic Quick Start](quick-start.md)
2. [Advanced Operations Playbook](advanced-playbook.md)
3. [atomic.starter Guide](atomic-starter.md)
4. [atomic.contract Guide](atomic-contract.md)
5. [atomic.app Guide](atomic-app.md)
6. [atomic.storage Guide](atomic-storage.md)
7. [atomic.spring.web Guide](atomic-spring-web.md)
8. [atomic.spring.idempotency Guide](atomic-spring-idempotency.md)
9. [atomic.spring.security Guide](atomic-spring-security.md)
10. [atomic.spring.oauth2 Guide](atomic-spring-oauth2.md)
11. [atomic.heartbeat Guide](atomic-heartbeat.md)
12. [Property Reference by Module](environment-variables.md)
