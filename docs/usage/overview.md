# Usage Overview

> Status: Development in progress (pre-release).
> Currently under development (pre-release).

## Before You Start

- Tested baseline: Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.3`
- Additional supported combinations are validated by CI matrix in `.github/workflows/ci.yml`.
- Other patch/minor versions are not guaranteed. Validate in your CI before rollout.
- Use `atomic.starter` to enable conditional auto-configuration.
- Add `atomic.contract` when your app directly uses `BaseResponse` / `HttpStatusException`.
- Add only the feature modules you use.

Recommended entry docs:
- Start with minimal setup: [Atomic Quick Start](quick-start.md)
- Move to production criteria: [Advanced Operations Playbook](advanced-playbook.md)
- Track in-progress breaking changes: [Migration Guide: v0.0.1 -> next](../migration/v0.0.1-to-next.md)

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

## 2. Dependencies

Current public publish workflow scope (Maven Central):

```kotlin
dependencies {
  implementation("com.infosung:atomic.contract:0.0.1")
  implementation("com.infosung:atomic.storage:0.0.1")
  implementation("com.infosung:atomic.spring.web:0.0.1")
  implementation("com.infosung:atomic.spring.security:0.0.1")
  implementation("com.infosung:atomic.spring.idempotency:0.0.1")
  implementation("com.infosung:atomic.spring.oauth2:0.0.1")
  implementation("com.infosung:atomic.heartbeat:0.0.1")
  implementation("com.infosung:atomic.starter:0.0.1")
  implementation("com.infosung:atomic.app:0.0.1")
}
```

Current `.github/workflows/publish-maven-central.yml` publishes:

- `atomic-contract`
- `atomic-storage`
- `atomic-spring-web`
- `atomic-spring-security`
- `atomic-spring-idempotency`
- `atomic-spring-oauth2`
- `atomic-heartbeat`
- `atomic-starter`
- `atomic-app`
- `atomic-app:app-version`
- `atomic-app:oauth-redirect`
- `atomic-app:storage-api`

Local multi-module adoption is still available when you need source-level customization:

```kotlin
dependencies {
  implementation(project(":atomic-starter"))
  implementation(project(":atomic-contract"))

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
| App-ready version API | `app.version` | enable `atomic.app.version.enabled`, provision `service_version` schema, and seed version rows |
| App-ready image upload/delete API | `app.storage.api` + `starter` + `storage` | enable `atomic.app.image.enabled`, provision `image` schema, and configure storage backends |
| App-ready OAuth redirect/callback relay | `app.oauth.redirect` + `starter` + `spring.oauth2` | enable `atomic.app.oauth.redirect.enabled`, consume `relayCode` in login API, configure store prerequisites (default `store.type=entity`), and set non-empty `allowed-redirect-uri-prefixes` |
| Convenience bundle for multiple app APIs | `app` + any prerequisites still required by enabled features | enable only the specific `atomic.app.*.enabled` tracks you need |
| Object storage and media processing | `starter` + `storage` | set `atomic.storage.backends.*` and use `ImageService` |
| Exception response standardization | `starter` + `spring.web` (+ `contract` when app directly uses `BaseResponse` / `HttpStatusException`) | `BaseExceptionHandler` subclass |
| API request/response audit logs | `starter` + `spring.web` | add `LogSaver` + `ApiLogAspect` implementation |
| API rate-limit filter | `starter` + `spring.web` | enable `atomic.web.rate-limit.enabled`, choose store (`auto/in-memory/redis/custom`), and review key policies (`path-key-strategy`, `missing-key-policy`, `ip.trust-forwarded-headers`) |
| HTTP idempotency (POST replay-safe) | `starter` + `spring.idempotency` | enable `atomic.idempotency.enabled`, configure key/ttl, and choose replay headers/body-cache limit |
| JWT auth for your API | `starter` + `spring.security` | set `atomic.security.jwt.*` and apply `JwtSecurityConfigurerAdapter` |
| Social login redirect flow | `starter` + `spring.oauth2` | set `atomic.oauth2.state.*` + `atomic.oauth2.providers.*`, and define one-time state store strategy (`in-memory-store.enabled=true` for single-node or custom/shared store) |
| Heartbeat ping + dependency checks | `starter` + `heartbeat` | enable `atomic.heartbeat.enabled`, set provider URL, optional DB/Redis checks and dedup mode |
| Full typical server stack | all modules | start with `starter` -> `storage/web` -> `security` -> `oauth2` |

## 5. Configuration Policy

- `atomic.starter` activates only when corresponding module classes are on classpath.
- `atomic.app` APIs are disabled by default and enabled by `atomic.app.version.enabled` / `atomic.app.image.enabled` / `atomic.app.oauth.redirect.enabled`.
- Some features still require application-specific beans (for example `BaseExceptionHandler`, `ApiLogAspect`, `LogSaver`).
- `atomic.app.version`, `atomic.app.image`, and `atomic.app.oauth.redirect` now ship controller-specific `HttpStatusException` mapping, so their documented `400/403/404` wire contract works without host-app exception advice.
- Host apps can still override app-module error responses with a higher-precedence `@RestControllerAdvice` when a custom envelope is required.
- Outside those app controllers, `HttpStatusException` still needs application-level mapping (for example `BaseExceptionHandler` subclass) to guarantee wire status and response shape.
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

## 6. Operational Checklist (All Modules)

- Keep secrets out of source code and inject via environment.
- Fix timezone/clock policy once and use consistently.
- Define one response format policy for all controllers.
- Verify error mapping behavior in non-200 scenarios.
- If you override app-module exception mapping, verify that your advice precedence still preserves the documented wire contract.
- Add integration tests for auth and callback flows before release.

## 7. Troubleshooting Index

- Response shape mismatch: check `BaseResponse` usage consistency.
- Storage upload `Unknown storageType`: check storage client/profile map keys and call-site `storageType` value.
- Image delete `stored storageType is unavailable for image delete`: check whether configured storage client keys still match persisted `ImageEntity.storageType` values.
- App image API setup may fail startup (not only API skip): check `atomic.app.image.enabled=true` with `ImageService`/`storageClients` bean availability.
- Image upload returned no thumbnail fields: check `atomic.app.image.thumbnail-enabled` and the request-level `thumbnailEnabled` override.
- Image delete rows remain with `DELETE_PENDING`: a previous storage delete failed; keep the stored `storageType` mapping available and retry delete after storage/backend recovery.
- App oauth redirect setup can fail in two ways: startup fail (for example default `store.type=entity` + missing required beans with `store.fail-fast=true`) or conditional endpoint skip/`404` (for example missing `OauthStateManager`/`OauthServiceProvider`); check selected store and OAuth bean prerequisites together.
- OAuth callback errors: check state and redirect URI mapping, and callback-binding failure type (`state is missing`, `cookie is missing`, `token mismatch`, `cookie is ambiguous`).
- OAuth relay cache store startup failure: check `atomic.app.oauth.redirect.store.cache.cache-name` exists in `CacheManager` (with default `store.fail-fast=true`).
- OAuth redirect prefix configuration issue: empty `allowed-redirect-uri-prefixes` fails startup; invalid prefix format returns `400` at redirect/callback request time.
- JWT unauthorized unexpectedly: check channel resolver and token source.
- API logs missing: check `ServiceLogger.send()` scheduling.
- Heartbeat fail flood or silence: verify `atomic.heartbeat.dedup.mode`, leader backend bean availability, and ping/check intervals.
- Artifact not found: switch to `project(":...")` dependency until repository resolution is ready.

## 8. Recommended Reading Order

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
