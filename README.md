# Atomic

Atomic is a Kotlin/Spring library suite for backend services.

> Status: Development in progress (pre-release).
> 현재 개발 중(배포 전)입니다.

## Tested Baseline

- Java `25`
- Kotlin `2.3.10`
- Spring Boot `4.0.3`
- `atomic.spring.web` AOP dependency uses `org.springframework.boot:spring-boot-starter-aspectj` (BOM-managed).

## Starter-First Policy

`atomic-starter` is the entrypoint for auto-configuration, but it does **not** pull feature modules transitively.

`atomic.app` is also a separate feature module.
It is **not included** in `atomic-starter`, and adding `atomic-starter` alone does not activate app APIs.

You must add:

1. `atomic-starter`
2. `atomic.contract` (required when your app directly uses `BaseResponse` / `HttpStatusException`)
3. only the feature modules you want (`storage`, `spring.web`, `spring.idempotency`, `spring.security`, `spring.oauth2`, `heartbeat`, `app`)

If a feature module is not on classpath, its auto-configuration is skipped.

Relationship summary:

- `atomic.starter`: common infra auto-config entrypoint
- `atomic.app`: app-level API bundle module (independent from starter)
- You can use `atomic.app` without starter for version API (JPA required).
- Image API in `atomic.app` needs storage beans, so typical setup is `atomic.app` + `atomic.starter` + `atomic.storage`.
- OAuth redirect API in `atomic.app` needs OAuth beans (`OauthServiceProvider`, `OauthStateManager`), typically from `atomic.starter` + `atomic.spring.oauth2`.

## Dependency Setup

### Current public publish workflow scope

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
For modules outside that workflow scope (`starter`, `storage`, `app`, `spring-idempotency`, `spring-oauth2`, `heartbeat`), use local multi-module dependencies or your internal publish pipeline.

### Multi-module local setup

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

## Feature Activation Matrix

| Feature | Required dependency | Activation properties | App-side required components |
|---|---|---|---|
| Contract utilities (`TimeProvider`, `TraceIdGenerator`) | `atomic.starter` + `atomic.contract` | none | use this when app directly uses `BaseResponse` / `HttpStatusException` |
| Storage (`storageClients`, `storageProfiles`, `ImageService`) | `atomic.starter` + `atomic.storage` | `atomic.storage.enabled=true` (default) and valid `atomic.storage.backends.*` | none |
| Common version check API (`GET /api/v1/version/check`) | `atomic.app` (+ datasource/JPA) | `atomic.app.version.enabled=true` | `service_version` table schema and version policy data |
| Common image upload/delete API (`POST/DELETE /api/v1/storage/image/{service}/{storageService}`) | `atomic.app` + `atomic.starter` + storage backend config | `atomic.app.image.enabled=true`, `atomic.storage.enabled=true` (+ optional uploader tracking config) | `image` table schema |
| Common OAuth redirect/callback relay API (`/oauth/redirect`, `/oauth/callback`) | `atomic.app` + `atomic.starter` + `atomic.spring.oauth2` | `atomic.app.oauth.redirect.enabled=true`, oauth state/provider properties | login API that consumes relayCode |
| Web logging/json/rate-limit helpers | `atomic.starter` + `atomic.spring.web` | `atomic.web.enabled=true` (default), `atomic.web.logging.enabled=true` (default), `atomic.web.rate-limit.enabled=false` (default) | for logging/exception mapping: `LogSaver` + `ApiLogAspect` + `BaseExceptionHandler`; for rate-limit only: no mandatory app bean |
| HTTP idempotency filter | `atomic.starter` + `atomic.spring.idempotency` | `atomic.idempotency.enabled=true` | optional custom `IdempotencyStore`, optional custom `IdempotencyFingerprintResolver` |
| Security JWT helpers | `atomic.starter` + `atomic.spring.security` | `atomic.security.enabled=true` (default), `atomic.security.jwt.enabled=true` (default), JWT keys | your `SecurityFilterChain` that applies `JwtSecurityConfigurerAdapter` |
| OAuth provider beans/service | `atomic.starter` + `atomic.spring.oauth2` | `atomic.oauth2.enabled=true` (default), `atomic.oauth2.state.enabled=true` (default), `atomic.oauth2.state.signing-secret`, `atomic.oauth2.state.in-memory-store.enabled=false` (default), per-provider `enabled=true` | callback/redirect controller endpoints |
| Heartbeat ping + dependency checks (`db`, `redis`) | `atomic.starter` + `atomic.heartbeat` | `atomic.heartbeat.enabled=true` | monitor endpoint URL config, optional DataSource/Redis, optional leader dedup backend |

## Minimal application.yml (starter-based)

```yaml
atomic:
  app:
    version:
      enabled: true
      endpoint-path: /api/v1/version/check
      default-store-url: https://www.infosung.com
    image:
      enabled: true
      endpoint-path: /api/v1/storage/image
      default-quality: 1.0
      min-quality: 0.1
      max-quality: 1.0
      uploader-parameter-enabled: false
      uploader-parameter-name: uploaderId
    oauth:
      redirect:
        enabled: true
        redirect-endpoint-path: /oauth/redirect
        callback-endpoint-path: /oauth/callback # base path. final callback path is /{provider} or /apple
        relay-code-query-parameter-name: relayCode
        relay-code-ttl-seconds: 300 # must be > 0
        store:
          type: entity # in-memory, cache, entity
          fail-fast: true
          in-memory:
            cleanup-interval: 100 # <= 0 disables periodic expired-entry cleanup
          cache:
            cache-name: atomicOauthRelayCode # must exist in CacheManager at startup
            key-prefix: atomic:oauth:relay:
            ttl-seconds: 300 # optional, must be > 0
          entity:
            table-name: atomic_oauth_relay_code # only [A-Za-z0-9_]
        allowed-redirect-uri-prefixes:
          - https://app.example.com

  storage:
    enabled: true
    backends:
      S3:
        enabled: true
        type: s3 # supported: s3, r2, minio
        bucket: ${ATOMIC_STORAGE_BUCKET}
        cdn: ${ATOMIC_STORAGE_CDN}
        region: ${ATOMIC_STORAGE_REGION}
        endpoint: ${ATOMIC_STORAGE_ENDPOINT:}
        path-style-access-enabled: ${ATOMIC_STORAGE_PATH_STYLE:false}
        access-key-id: ${ATOMIC_STORAGE_ACCESS_KEY_ID:}
        secret-access-key: ${ATOMIC_STORAGE_SECRET_ACCESS_KEY:}
        session-token: ${ATOMIC_STORAGE_SESSION_TOKEN:}

  web:
    enabled: true
    logging:
      enabled: true
      queue-size: ${ATOMIC_WEB_LOG_QUEUE_SIZE:10000}
      filter:
        enabled: true
        order: 1
        url-patterns:
          - /*
    rate-limit:
      enabled: false
      store: auto # auto, in-memory, redis, custom
      limit: 100
      window-seconds: 60
      include-methods: [GET, POST, PUT, PATCH, DELETE]
      exclude-path-prefixes: [/actuator]
      path-key-strategy: rule-prefix # rule-prefix, request-uri
      key-strategy: ip # ip, header
      ip:
        trust-forwarded-headers: false
      key-header-name: X-User-Id
      missing-key-policy: reject # reject, skip
      fail-open: true
      response-body: Too many requests.
      in-memory:
        cleanup-interval: 1000
      redis:
        key-prefix: atomic:ratelimit:
      filter:
        order: -100
        url-patterns:
          - /*

  idempotency:
    enabled: false
    header-name: Idempotency-Key
    ttl-seconds: 300
    processing-ttl-seconds: 3600
    require-header: true
    include-methods: [POST]
    fail-open: true
    replay-header-name: X-Idempotent-Replay
    replay-body-omitted-header-name: X-Idempotent-Replay-Body-Omitted
    max-cached-body-bytes: 262144
    in-memory:
      cleanup-interval: 1000
    filter:
      enabled: true
      order: -50
      url-patterns:
        - /*

  security:
    enabled: true
    jwt:
      enabled: true
      access-key: ${ATOMIC_SECURITY_JWT_ACCESS_KEY}
      refresh-key: ${ATOMIC_SECURITY_JWT_REFRESH_KEY}
      service-name: ${ATOMIC_SECURITY_JWT_SERVICE_NAME:MyService}
      access-expired-second: ${ATOMIC_SECURITY_JWT_ACCESS_EXPIRED_SECOND:3600}
      refresh-expired-second: ${ATOMIC_SECURITY_JWT_REFRESH_EXPIRED_SECOND:1209600}

  oauth2:
    enabled: true
    state:
      enabled: true
      signing-secret: ${ATOMIC_OAUTH2_STATE_SIGNING_SECRET} # must be >= 32 bytes
      issuer: ${ATOMIC_OAUTH2_STATE_ISSUER:atomic-oauth-state}
      ttl-seconds: ${ATOMIC_OAUTH2_STATE_TTL_SECONDS:300}
      in-memory-store:
        enabled: false # default false (recommended for multi-instance)
    providers:
      google:
        enabled: ${ATOMIC_OAUTH2_GOOGLE_ENABLED:false}
        client-id: ${ATOMIC_OAUTH2_GOOGLE_CLIENT_ID:}
        client-secret: ${ATOMIC_OAUTH2_GOOGLE_CLIENT_SECRET:}
        server-redirect-uri: ${ATOMIC_OAUTH2_GOOGLE_SERVER_REDIRECT_URI:}
      kakao:
        enabled: ${ATOMIC_OAUTH2_KAKAO_ENABLED:false}
        client-id: ${ATOMIC_OAUTH2_KAKAO_CLIENT_ID:}
        server-redirect-uri: ${ATOMIC_OAUTH2_KAKAO_SERVER_REDIRECT_URI:}
      apple:
        enabled: ${ATOMIC_OAUTH2_APPLE_ENABLED:false}
        client-id: ${ATOMIC_OAUTH2_APPLE_CLIENT_ID:}
        server-redirect-uri: ${ATOMIC_OAUTH2_APPLE_SERVER_REDIRECT_URI:}

  heartbeat:
    enabled: false
    scheduler-thread-prefix: atomic-heartbeat
    ping:
      interval: 30s
      send-start-event: false
      fail-open: true
    provider:
      type: healthchecks # healthchecks, custom
      connect-timeout: 1s
      timeout: 2s
      instance-id: ${HOSTNAME:default}
      healthchecks:
        base-url: ${ATOMIC_HEARTBEAT_BASE_URL:}
        success-path: ""
        fail-path: /fail
        start-path: /start
    checks:
      missing-bean-policy: warn # warn, fail
      db:
        enabled: false
        required: true
        interval: 30s
        timeout: 2s
        query: SELECT 1
      redis:
        enabled: false
        required: true
        interval: 30s
        timeout: 2s
    dedup:
      mode: none # none, leader, per-instance
      leader:
        backend: redis # redis, jdbc, custom
        owner-id: ${HOSTNAME:}
        lease-duration: 45s
        renew-interval: 15s
        redis:
          key: atomic:heartbeat:leader
        jdbc:
          table-name: atomic_heartbeat_leader
          lock-name: default
          auto-create-table: false
```

Heartbeat operational notes:

- `atomic.heartbeat.provider.type=custom` requires a custom `HeartbeatProvider` bean.
- `atomic.heartbeat.dedup.leader.backend=custom` requires a custom `LeaderElector` bean.
- in `leader + jdbc` mode, keep `auto-create-table=false` for production and prepare lock table by migration.
- in `per-instance` mode, use instance-specific monitor URL template (`{instanceId}`) to avoid signal collisions.

## Environment Variable Checklist

At minimum by feature:

- Storage
  - `ATOMIC_STORAGE_BUCKET`, `ATOMIC_STORAGE_CDN`, `ATOMIC_STORAGE_REGION`
  - optional credentials: `ATOMIC_STORAGE_ACCESS_KEY_ID`, `ATOMIC_STORAGE_SECRET_ACCESS_KEY`, `ATOMIC_STORAGE_SESSION_TOKEN`
- App APIs
  - no dedicated env var is mandatory
  - `atomic.app.version.default-store-url` can be environment-backed when needed
- Security
  - `ATOMIC_SECURITY_JWT_ACCESS_KEY`, `ATOMIC_SECURITY_JWT_REFRESH_KEY`
- OAuth2
  - `ATOMIC_OAUTH2_STATE_SIGNING_SECRET` (must be at least 32 bytes, otherwise startup fails)
  - provider specific values (for enabled providers only)
- Heartbeat
  - `ATOMIC_HEARTBEAT_BASE_URL` (required when `provider.type=healthchecks`)

## Component Registration You Still Need

### 1) Web module

Implement/register:

- `LogSaver` and `ApiLogAspect` subclass when API logging is enabled
- `BaseExceptionHandler` subclass when exception-response mapping is needed
- for explicit custom mode, set `atomic.web.rate-limit.store=custom` and register `RateLimitStore`.
- any user-defined `RateLimitStore` bean overrides starter default store via `@ConditionalOnMissingBean` (regardless of `store` mode).
- rate-limit rule matching is first-match wins, and `X-RateLimit-Reset`/`Retry-After` are seconds until current fixed-window boundary.
- for `key-strategy=header`, missing header key is rejected by default (`missing-key-policy=reject`).
- `atomic.web.rate-limit.redis.key-prefix` must be non-blank when store mode is `redis` or `auto`.
- with `key-strategy=ip`, default actor key is `remoteAddr`. set `ip.trust-forwarded-headers=true` only behind trusted proxy/ingress that sanitizes forwarding headers.
- effective rate-limit key format is `actor|method|pathKey`. with default `path-key-strategy=rule-prefix`, unmatched routes share `pathKey=default`.

### 2) Security module

Register `SecurityFilterChain` and apply auto-configured `JwtSecurityConfigurerAdapter`.
- fail-fast policy: when `atomic.security.enabled=true`, `JwtSecurityConfigurerAdapter` requires
  `JwtProvider` (auto by keys or custom bean). missing provider fails startup.
  - if `atomic.security.jwt.enabled=false`, register custom `JwtProvider` or disable security auto-config.

### 3) Idempotency module

`atomic.spring.idempotency` provides filter/core types.

- default store is in-memory (process-local).
- production multi-instance services should register custom `IdempotencyStore` (shared backend).
- optional custom `IdempotencyFingerprintResolver` can add request-body-aware fingerprinting.
- `Idempotency-Key` should be namespaced by actor/tenant (`userId:key`) to avoid cross-client collisions.
- `max-cached-body-bytes` is a hard capture limit for replay cache body.
- replay excludes non-replayable headers (`Set-Cookie`, hop-by-hop/dynamic headers like `Connection`, `Transfer-Encoding`, `Date`, `Server`).
- `processing-ttl-seconds` is used for in-flight lock duration, while `ttl-seconds` is used for completed replay entry TTL.
- non-5xx responses (including 4xx) are cached/replayed for the same key; 5xx/exception path removes active key.
- default filter order runs rate-limit (`-100`) before idempotency (`-50`) so duplicate requests are still throttled.
- replay responses always include `X-Idempotent-Replay=true`; when cached body is omitted by size limit, `X-Idempotent-Replay-Body-Omitted=true` is also set.

### 4) OAuth2 module

If you do **not** use `atomic.app.oauth.redirect`, implement callback endpoints (for example `/oauth/redirect/{provider}`, `/oauth/callback/{provider}`) and integrate with `OauthServiceProvider`.
If you use `atomic.app.oauth.redirect.enabled=true`, `AppOauthRedirectController` provides common redirect/callback endpoints.

### 5) App module (`atomic.app`)

`atomic.app` provides ready-to-use APIs:

- version check (`AppVersionController`)
- image upload/delete (`AppStorageController`)
- oauth redirect/callback relay (`AppOauthRedirectController`)

Prerequisites:

- Version API needs JPA datasource and `service_version` table.
- Image API needs JPA datasource + `image` table + storage beans (`ImageService`, `storageClients`).
- if `atomic.app.image.enabled=true` and image/storage beans are missing, app startup can fail (not just API skipped).
- OAuth redirect API needs `OauthServiceProvider` and `OauthStateManager` beans.
- OAuth relay store default is `entity`, so default setup also needs `DataSource`, `PlatformTransactionManager`, and `ObjectMapper`.
- with default `store.type=entity` + `store.fail-fast=true`, missing dependencies fail startup.
- when `store.type=in-memory` or `store.type=cache`, entity(db) dependency validation is skipped.
- if your service uses only in-memory/cache relay and has no datasource, disable JDBC auto-config or provide datasource config.
  - example: `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`
- if `store.type=entity` (default), prepare relay table (`atomic_oauth_relay_code` or configured table-name) before rollout.
- Login API should consume relay payload using `AppOauthRelayCodeService.consumeRelayCode(relayCode)`.

### 6) Heartbeat module (`atomic.heartbeat`)

- `provider.type=custom`: register `HeartbeatProvider` bean explicitly.
- `dedup.leader.backend=custom`: register `LeaderElector` bean explicitly.
- `dedup.mode=leader` + `backend=jdbc`: provision lock table before rollout (recommended), keep `auto-create-table=false` in production.

Image uploader identity option (without security coupling):

- `atomic.app.image.uploader-parameter-enabled=true` enables uploader parameter enforcement.
- `atomic.app.image.uploader-parameter-name` defines which request parameter to use (for example `memberId`).
- upload stores that value in `ImageEntity.uploaderId`.
- delete requires same parameter value and rejects mismatch (`403`).
- when enabled in production, align `image` table with nullable `uploader_id` column.

OAuth relay option (without token in callback query):

- `atomic.app.oauth.redirect.enabled=true` enables redirect/callback endpoints.
- callback redirects frontend with `relayCode` only (no raw `id_token`/`access_token` in URL).
- login API consumes relay payload via `AppOauthRelayCodeService.consumeRelayCode(relayCode)`.
- redirect endpoint input `redirectUri` must be an absolute URI and must not include user-info.
- configure `allowed-redirect-uri-prefixes` for production.
  - matching uses scheme/host/port/path-prefix boundary (not raw string startsWith).
  - each entry must be an absolute URI without query/fragment.
  - invalid entry format is detected at redirect/callback request time.
  - current behavior: invalid entry format is returned as `400`.
  - example: `https://app.example.com/oauth` allows `https://app.example.com/oauth/callback` but rejects `https://app.example.com.evil.com/...`.
- if `allowed-redirect-uri-prefixes` is empty, any absolute `redirectUri` is accepted.
- relay store type default is `entity` (`atomic.app.oauth.redirect.store.type=entity`).
- selected relay store dependencies are validated (`in-memory`/`cache`/`entity`); unselected store dependencies are not validated.
- global relay settings (for example `relay-code-ttl-seconds`) are validated regardless of store type.
- provider callback path mapping:
  - Google/Kakao: `https://{host}{callback-endpoint-path}/{provider}`
  - Apple: `https://{host}{callback-endpoint-path}/apple` (`POST`, `form_post`)
- `GET {callback-endpoint-path}/apple` is rejected with `400` (Apple callback supports `POST` only).
- keep provider `server-redirect-uri` registration aligned with mapping above.
- `cache` uses Spring `CacheManager` (for example Redis cache), `entity` uses datasource/transaction manager.
- `relay-code-ttl-seconds` must be greater than zero (validated at startup).
- `store.type=cache` requires configured `cache-name` to exist in `CacheManager` at startup.
- `store.type=entity` table name allows only letters, numbers, and underscores.
- cache/entity stores validate expiration on consume (`pop`) and remove consumed relay data.
- for cache backends, configure backend TTL/eviction to avoid stale expired keys accumulating.
- for entity store, run periodic cleanup (for example `DELETE FROM atomic_oauth_relay_code WHERE expires_at <= NOW()`) for unconsumed expired rows.
- HTTP status semantics assume your app maps `HttpStatusException` via exception handler configuration.
- OAuth callback/state errors from oauth module are mapped to `HttpStatusException(400)` in app oauth redirect service.
- with `store.fail-fast=false`, selected store errors (missing deps, invalid cache-name/ttl, unavailable cache) do not fail startup and fall back to in-memory store.
- in-memory fallback is process-local per instance and can break relay one-time guarantees in multi-instance deployments.

**Important (Spring Security):**
When you use Spring Security, explicitly include the storage API path in your security authorization rules.
Protect `POST/DELETE /api/v1/storage/image/**` (or your custom `atomic.app.image.endpoint-path/**`) as authenticated/authorized endpoints.

## OAuth Provider Notes (starter)

- Providers are independent; enable only what you use.
- You do **not** need to enable Google/Kakao/Apple all together.
- Multi-client mode is supported per provider:
  - use `clients.{clientKey}.*`
  - set `default-client-key` when multiple clients exist
  - keep audience values unique per client
- For multi-instance production, prefer a shared/distributed `OauthStateStore` instead of default in-memory store.
- `atomic.oauth2.state.in-memory-store.enabled` default is `false` because in-memory state store is process-local and can break callbacks in multi-instance deployments.

## Customization / Override Strategy

Most starter beans use `@ConditionalOnMissingBean`, so you can override by registering your own bean.
For beans guarded by name, use the same bean name.

Typical override points:

- Storage
  - `storageClients` (bean name)
  - `storageProfiles` (bean name)
  - `ImageObjectKeyGenerator`, `ImageInputValidator`, `ImageMetadataReader`, `ImageThumbnailGenerator`
- Web
  - `JsonTransfer`, `ServiceLogger`, `ApiLogFilter`
  - `apiLogFilterRegistration` (bean name)
- Security
  - `ClientChannelResolver`, `SecurityCookiePolicy`, `JwtProvider`, `JwtSecurityConfigurerAdapter`
- OAuth2
  - `atomicOauthRestClient` (bean name)
  - `OauthStateStore`, `OauthStateManager`, `OauthServiceProvider`
  - `googleOauthProvider`, `kakaoOauthProvider`, `appleOauthProvider` (bean names)
- App
  - `appVersionCheckService`, `appVersionController`
  - `appImageApiService`, `appStorageController`

## What Was Ambiguous Before (Review Summary)

Before this README rewrite, the main ambiguity points were:

1. `atomic-starter` alone looks sufficient, but feature modules must be added explicitly.
2. Module-specific mandatory properties were spread across separate docs.
3. App-side required components (`LogSaver`, `ApiLogAspect`, `SecurityFilterChain`, callback controller) were not visible in one place.
4. Environment variable setup examples were missing from root-level onboarding.

This README now consolidates those points into one starter-first onboarding path.

## Detailed Guides

- [Usage Overview](docs/usage/overview.md)
- [atomic.starter Guide](docs/usage/atomic-starter.md)
- [atomic.contract Guide](docs/usage/atomic-contract.md)
- [atomic.app Guide](docs/usage/atomic-app.md)
- [atomic.storage Guide](docs/usage/atomic-storage.md)
- [atomic.spring.web Guide](docs/usage/atomic-spring-web.md)
- [atomic.spring.idempotency Guide](docs/usage/atomic-spring-idempotency.md)
- [atomic.spring.security Guide](docs/usage/atomic-spring-security.md)
- [atomic.spring.oauth2 Guide](docs/usage/atomic-spring-oauth2.md)
- [atomic.heartbeat Guide](docs/usage/atomic-heartbeat.md)

---
Developed with Codex.
