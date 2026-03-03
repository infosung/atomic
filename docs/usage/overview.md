# Usage Overview

> Status: Development in progress (pre-release).
> 현재 개발 중(배포 전)입니다.

## Before You Start

- Tested baseline: Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.3`
- Additional supported combinations are validated by CI matrix in `.github/workflows/ci.yml`.
- Other patch/minor versions are not guaranteed. Validate in your CI before rollout.
- Use `atomic.starter` to enable conditional auto-configuration.
- Add `atomic.contract` when your app directly uses `BaseResponse` / `HttpStatusException`.
- Add only the feature modules you use.

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
- `atomic.app`: common app-level API bundle module (`version` + `storage-api` + `oauth-redirect`)
- `atomic.spring.web`: API error handling, request/response logging, RestTemplate interceptor/error handler
- `atomic.spring.idempotency`: HTTP idempotency filter for one-time POST processing
- `atomic.spring.security`: JWT issue/verify + Spring Security filter integration
- `atomic.spring.oauth2`: OAuth provider integration (Google/Kakao/Apple), redirect flow and id token/userinfo identity resolution

## 2. Dependencies

Current public publish workflow scope (Maven Central):

```kotlin
dependencies {
  implementation("com.infosung:atomic.contract:0.0.1")
  implementation("com.infosung:atomic.spring.web:0.0.1")
  implementation("com.infosung:atomic.spring.security:0.0.1")
}
```

Current `.github/workflows/publish-maven-central.yml` publishes only:

- `atomic-contract`
- `atomic-spring-web`
- `atomic-spring-security`

`atomic.starter` is also outside current public publish workflow scope.
For full-stack adoption (`starter`, `storage`, `app`, `spring-idempotency`, `spring-oauth2`), use local module dependencies (or your internal artifact repository):

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
}
```

## 3. Quick Start (First Day)

1. Add `atomic.starter` and `atomic.contract` first.
2. Add one feature module only (`app`, `storage`, `web`, `idempotency`, `security`, or `oauth2`).
3. Register only minimum required beans from that guide.
4. Run one smoke endpoint.
5. Add optional features after baseline works.

## 4. Module Combination Matrix

| Goal | Modules | First Setup |
|---|---|---|
| Standard API response + shared contracts | `starter` + `contract` | Use `BaseResponse`, `HttpStatusException` |
| App-ready version/image APIs | `app` + (`starter` + `storage` for image API) | enable `atomic.app.version.enabled` and/or `atomic.app.image.enabled` |
| App-ready OAuth redirect/callback relay | `app` + `starter` + `spring.oauth2` | enable `atomic.app.oauth.redirect.enabled`, consume `relayCode` in login API, and configure store prerequisites (default `store.type=entity`) |
| Object storage and media processing | `starter` + `storage` | set `atomic.storage.backends.*` and use `ImageService` |
| Exception response standardization | `starter` + `contract` + `spring.web` | `BaseExceptionHandler` subclass |
| API request/response audit logs | `starter` + `contract` + `spring.web` | add `LogSaver` + `ApiLogAspect` implementation |
| API rate-limit filter | `starter` + `contract` + `spring.web` | enable `atomic.web.rate-limit.enabled`, choose store (`auto/in-memory/redis/custom`), and review key policies (`path-key-strategy`, `missing-key-policy`, `ip.trust-forwarded-headers`) |
| HTTP idempotency (POST replay-safe) | `starter` + `contract` + `spring.idempotency` | enable `atomic.idempotency.enabled`, configure key/ttl, and choose replay headers/body-cache limit |
| JWT auth for your API | `starter` + `contract` + `spring.security` | set `atomic.security.jwt.*` and apply `JwtSecurityConfigurerAdapter` |
| Social login redirect flow | `starter` + `contract` + `spring.oauth2` | set `atomic.oauth2.state.*` + `atomic.oauth2.providers.*` |
| Full typical server stack | all modules | start with `starter` -> `storage/web` -> `security` -> `oauth2` |

## 5. Configuration Policy

- `atomic.starter` activates only when corresponding module classes are on classpath.
- `atomic.app` APIs are disabled by default and enabled by `atomic.app.version.enabled` / `atomic.app.image.enabled` / `atomic.app.oauth.redirect.enabled`.
- Some features still require application-specific beans (for example `BaseExceptionHandler`, `ApiLogAspect`, `LogSaver`).
- OAuth provider beans are registered only when `atomic.oauth2.state.enabled=true`, `atomic.oauth2.state.signing-secret` is set, and each provider `enabled=true`.
- `atomic.oauth2.state.signing-secret` must be at least 32 bytes; shorter values fail startup.
- Rate-limit rules are evaluated in declaration order (first match wins), and reset/retry headers follow fixed-window boundary seconds.
- Rate-limit storage key is `actor|method|pathKey`; with default `path-key-strategy=rule-prefix`, unmatched routes share one `default` bucket.
- When both are enabled, run rate-limit before idempotency (starter defaults: rate-limit `-100`, idempotency `-50`).
- Start with minimum modules and add optional beans as needed.

## 6. Operational Checklist (All Modules)

- Keep secrets out of source code and inject via environment.
- Fix timezone/clock policy once and use consistently.
- Define one response format policy for all controllers.
- Verify error mapping behavior in non-200 scenarios.
- Add integration tests for auth and callback flows before release.

## 7. Troubleshooting Index

- Response shape mismatch: check `BaseResponse` usage consistency.
- Storage upload `Unknown storageType`: check storage client/profile map keys and call-site `storageType` value.
- App image API setup may fail startup (not only API skip): check `atomic.app.image.enabled=true` with `ImageService`/`storageClients` bean availability.
- App oauth redirect setup may fail startup (default `store.type=entity`, `store.fail-fast=true`): check `atomic.app.oauth.redirect.enabled=true`, `OauthServiceProvider`, `OauthStateManager`, and selected relay store prerequisites.
- OAuth callback errors: check state and redirect URI mapping.
- OAuth relay cache store startup failure: check `atomic.app.oauth.redirect.store.cache.cache-name` exists in `CacheManager` (with default `store.fail-fast=true`).
- OAuth redirect prefix format issue: invalid `allowed-redirect-uri-prefixes` returns `400`; validate URI format in config/CI.
- JWT unauthorized unexpectedly: check channel resolver and token source.
- API logs missing: check `ServiceLogger.send()` scheduling.
- Artifact not found: switch to `project(":...")` dependency until repository resolution is ready.

## 8. Recommended Reading Order

1. [atomic.starter Guide](atomic-starter.md)
2. [atomic.contract Guide](atomic-contract.md)
3. [atomic.app Guide](atomic-app.md)
4. [atomic.storage Guide](atomic-storage.md)
5. [atomic.spring.web Guide](atomic-spring-web.md)
6. [atomic.spring.idempotency Guide](atomic-spring-idempotency.md)
7. [atomic.spring.security Guide](atomic-spring-security.md)
8. [atomic.spring.oauth2 Guide](atomic-spring-oauth2.md)
