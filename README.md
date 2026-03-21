# Atomic

Atomic is a Kotlin/Spring library suite for backend services.

> Status: `v0.0.3` documentation for a pre-1.0 release line.

## Tested Baseline

- Java `25`
- Kotlin `2.3.10`
- Spring Boot `4.0.3`
- `atomic.spring.web` AOP dependency uses `org.springframework.boot:spring-boot-starter-aspectj` (BOM-managed).
- `v0.0.3` release validation is written against this baseline. If you run a different runtime mix,
  treat it as self-validated in your own CI before rollout.

## Start Here (Recommended Path)

1. Minimal adoption: [Atomic Quick Start](docs/usage/quick-start.md)
2. Production transition: [Advanced Operations Playbook](docs/usage/advanced-playbook.md)
3. Upgrade from `v0.0.2`: [Release Migration Guide](docs/migration/v0.0.2-to-v0.0.3.md)
4. Module-level details: see `Detailed Guides` below

## Feature-First Onboarding

`atomic-starter` is the entrypoint for auto-configuration, but it does **not** pull feature modules transitively.

For new adoption, prefer the narrowest feature module that matches your use case:

1. `atomic.app.version`
2. `atomic.app.storage.api`
3. `atomic.app.oauth.redirect`

Use `atomic.app` only when you intentionally want the convenience bundle that combines all three app APIs.

`atomic.app` is **not included** in `atomic-starter`, and adding `atomic-starter` alone does not activate app APIs.

For starter-based path, add:

1. `atomic-starter`
2. `atomic.contract` (required when your app directly uses `BaseResponse` / `HttpStatusException`)
3. only the feature modules you want (`storage`, `spring.web`, `spring.idempotency`, `spring.security`, `spring.oauth2`, `heartbeat`, `app.version`, `app.storage.api`, `app.oauth.redirect`, or the convenience bundle `app`)

Exception:
- `atomic.app.version` can start without `atomic-starter` (see [Atomic Quick Start](docs/usage/quick-start.md)).

If a feature module is not on classpath, its auto-configuration is skipped.

Relationship summary:

- `atomic.starter`: common infra auto-config entrypoint
- `atomic.app.version`: narrow app-level version API module
- `atomic.app.storage.api`: narrow app-level image API module
- `atomic.app.oauth.redirect`: narrow app-level OAuth redirect/callback relay module
- `atomic.app`: app-level convenience bundle module (independent from starter)
- `atomic.app.version` can be used without starter when JPA/datasource are ready.
- `atomic.app.storage.api` needs storage beans, so typical setup is `atomic.app.storage.api` + `atomic.starter` + `atomic.storage`.
- `atomic.app.oauth.redirect` needs OAuth beans (`OauthServiceProvider`, `OauthStateManager`), typically from `atomic.starter` + `atomic.spring.oauth2`.

## Dependency Setup

### Published Artifact Examples (`v0.0.3`)

```kotlin
dependencies {
  implementation("com.infosung:atomic.contract:0.0.3")
  implementation("com.infosung:atomic.storage:0.0.3")
  implementation("com.infosung:atomic.spring.web:0.0.3")
  implementation("com.infosung:atomic.spring.security:0.0.3")
  implementation("com.infosung:atomic.spring.idempotency:0.0.3")
  implementation("com.infosung:atomic.spring.oauth2:0.0.3")
  implementation("com.infosung:atomic.heartbeat:0.0.3")
  implementation("com.infosung:atomic.starter:0.0.3")
  implementation("com.infosung:atomic.app.version:0.0.3")
  implementation("com.infosung:atomic.app.storage.api:0.0.3")
  implementation("com.infosung:atomic.app.oauth.redirect:0.0.3")
  implementation("com.infosung:atomic.app:0.0.3")
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
| Contract utilities (`TimeProvider`, `TraceIdGenerator`) | `atomic.contract` (starter optional) | none | In starter-based flow these are auto-configured; for direct usage, `atomic.contract` alone is enough |
| Storage (`storageClients`, `storageProfiles`, `ImageService`) | `atomic.starter` + `atomic.storage` | `atomic.storage.enabled=true` (default); configure at least one enabled `atomic.storage.backends.*` entry before storage API traffic | none |
| Common version check API (`GET /api/v1/version/check`) | `atomic.app.version` (+ datasource/JPA) or convenience bundle `atomic.app` with the same datasource/JPA prerequisites | `atomic.app.version.enabled=true` | `service_version` table schema and version policy data (`store_available` defaults to `true`; set it to `false` for review/pre-rollout rows that must not become force-update targets yet) |
| Common image upload/delete API (`POST/DELETE /api/v1/storage/image/{service}/{storageService}`) | `atomic.app.storage.api` + `atomic.starter` + `atomic.storage` + storage backend config, or convenience bundle `atomic.app` with the same storage prerequisites | `atomic.app.image.enabled=true`, `atomic.storage.enabled=true` (+ optional uploader tracking config) | `image` table schema |
| Common OAuth redirect/callback relay API (`/oauth/redirect/{provider}`, `/oauth/callback/{provider}`, `POST /oauth/callback/apple`) | `atomic.app.oauth.redirect` + `atomic.starter` + `atomic.spring.oauth2`, or convenience bundle `atomic.app` with the same starter/oauth2/store prerequisites | `atomic.app.oauth.redirect.enabled=true`, non-empty `allowed-redirect-uri-prefixes`, oauth state/provider properties, and selected relay-store prerequisites | login API that consumes relayCode and establishes app session/token |
| Web logging/json/rate-limit helpers | `atomic.starter` + `atomic.spring.web` | `atomic.web.enabled=true` (default), `atomic.web.logging.enabled=true` (default), `atomic.web.rate-limit.enabled=false` (default) | for logging/exception mapping: `LogSaver` + `ApiLogAspect` + `BaseExceptionHandler`; for rate-limit only: no mandatory app bean |
| HTTP idempotency filter | `atomic.starter` + `atomic.spring.idempotency` | `atomic.idempotency.enabled=true` | optional custom `IdempotencyStore`, optional custom `IdempotencyFingerprintResolver` |
| Security JWT helpers | `atomic.starter` + `atomic.spring.security` | `atomic.security.enabled=true` (default), `atomic.security.jwt.enabled=true` (default), JWT keys | your `SecurityFilterChain` that applies `JwtSecurityConfigurerAdapter` |
| OAuth provider beans/service | `atomic.starter` + `atomic.spring.oauth2` | `atomic.oauth2.enabled=true` (default), per-provider `enabled=true`, and available `OauthStateManager` (auto path: `state.enabled=true` + `state.signing-secret`; or custom bean) | callback/redirect controller endpoints |
| Heartbeat ping + dependency checks (`db`, `redis`) | `atomic.starter` + `atomic.heartbeat` | `atomic.heartbeat.enabled=true` | monitor endpoint URL config, optional DataSource/Redis, optional leader dedup backend |

### Compatibility Notes

- Version API host customization should target the exported `CheckAppVersionUseCase` bean. The module is now packaged in `application`, `domain`, and `adapter` layers, and host apps should avoid depending on internal implementation/support types beyond that use-case seam.
- OAuth redirect stops at relayCode handoff. Your app still owns the login/session exchange after `ConsumeOauthRelayCodeUseCase.consume(relayCode)` succeeds.
- Internally the module is now packaged in `application`, `domain`, and `adapter` layers. Supported host seams are the exported build/issue/consume use-case beans, the exported `OauthRelayCodeStore` seam, and the exported web adapter beans `AppOauthRedirectController` / `AppOauthRedirectHttpExceptionHandler`.
- `atomic.spring.oauth2` keeps legacy `Jwt`-returning seams for compatibility, but new integrations should prefer typed `OauthStateClaims` / typed id-token claim models where available. Provider registry startup now fails fast on duplicate `OauthProviderName` registration instead of silently shadowing one bean.

## Reference application.yml (feature template)

> Warning: this is a feature template, not a copy-paste minimal config. Enable only tracks you actually use.
> Warning: secret-like sample values below are for local bootstrap only. Replace before any shared/staging/prod deployment.

```yaml
atomic:
  app:
    version:
      enabled: false # enable only when version API prerequisites are ready
      endpoint-path: /api/v1/version/check
      default-store-url: https://www.infosung.com
    image:
      enabled: false # enable only when image table + storage beans are ready
      endpoint-path: /api/v1/storage/image
      default-quality: 1.0
      min-quality: 0.1
      max-quality: 1.0
      thumbnail-enabled: true
      uploader-parameter-enabled: false
      uploader-parameter-name: uploaderId
    oauth:
      redirect:
        enabled: false # enable only when oauth provider/state/store prerequisites are ready
        redirect-endpoint-path: /oauth/redirect
        callback-endpoint-path: /oauth/callback # base path. final callback path is /{provider} or /apple
        relay-code-query-parameter-name: relayCode
        relay-code-ttl-seconds: 300 # must be > 0
        callback-binding:
          enabled: true # legacy compatibility flag; prefer explicit mode below
          mode: strict # strict, relaxed, disabled
          state-attribute-key: atomicCallbackBinding
          cookie-name: __Host-atomic_oauth_callback_binding
          cookie-same-site: None
          cookie-path: /
          cookie-secure: true
          cookie-max-age-seconds: 600
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
        bucket: my-bucket
        cdn: https://cdn.example.com
        region: ap-northeast-2
        endpoint: ""
        path-style-access-enabled: false
        access-key-id: ""
        secret-access-key: ""
        session-token: ""

  web:
    enabled: true
    logging:
      enabled: true
      queue-size: 10000
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
      access-key: CHANGE_ME_WITH_STRONG_RANDOM_ACCESS_KEY_64B
      refresh-key: CHANGE_ME_WITH_STRONG_RANDOM_REFRESH_KEY_64B
      service-name: MyService
      access-expired-second: 3600
      refresh-expired-second: 1209600

  oauth2:
    enabled: true
    state:
      enabled: true
      signing-secret: CHANGE_ME_WITH_STRONG_RANDOM_STATE_SECRET_AT_LEAST_32_BYTES
      issuer: atomic-oauth-state
      ttl-seconds: 300
      in-memory-store:
        enabled: false # default false (recommended for multi-instance)
    providers:
      google:
        enabled: false
        client-id: ""
        client-secret: ""
        server-redirect-uri: ""
      kakao:
        enabled: false
        client-id: ""
        server-redirect-uri: ""
      apple:
        enabled: false
        client-id: ""
        server-redirect-uri: ""

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
      instance-id: default
      healthchecks:
        base-url: ""
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
        owner-id: ""
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

Production safety note:
- Do not copy availability-first defaults to production without review (`atomic.web.rate-limit.fail-open`, `atomic.idempotency.fail-open`, `atomic.heartbeat.ping.fail-open`, `atomic.heartbeat.checks.missing-bean-policy`).

## Property Checklist

Detailed reference: [Property Reference by Module](docs/usage/environment-variables.md)

At minimum by feature:

- Storage
  - `atomic.storage.backends.<backend>.bucket`, `cdn`, `region`
  - optional credentials: `access-key-id`, `secret-access-key`, `session-token`
- App APIs
  - no dedicated starter-level required property
  - `atomic.app.version.default-store-url` can be set from your config source when needed
- Security
  - `atomic.security.jwt.access-key`, `atomic.security.jwt.refresh-key` (auto `JwtProvider` path)
- OAuth2
  - `atomic.oauth2.state.signing-secret` (must be at least 32 bytes)
  - provider specific values (for enabled providers only)
  - if providers are enabled without available `OauthStateManager`, provider beans can be skipped and callback endpoints may be missing
- Heartbeat
  - `atomic.heartbeat.provider.healthchecks.base-url` (required when `provider.type=healthchecks`)

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
- fail-fast policy: when `atomic.security.enabled=true` and `ObjectMapper` bean exists,
  `JwtSecurityConfigurerAdapter` requires `JwtProvider` (auto by keys or custom bean). missing provider fails startup.
  - if `atomic.security.jwt.enabled=false`, register custom `JwtProvider` or disable security auto-config.
  - if `ObjectMapper` is missing, `JwtSecurityConfigurerAdapter` is not auto-registered.

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
If you use `atomic.app.oauth.redirect.enabled=true`, `AppOauthRedirectController` provides common redirect/callback endpoints from the web adapter boundary.

### 5) App module (`atomic.app`)

`atomic.app` provides ready-to-use APIs:

- version check (`AppVersionController`)
- image upload/delete (`adapter.in.web.AppStorageController`)
- oauth redirect/callback relay (`AppOauthRedirectController`)

For new adoption, prefer the narrow modules `atomic.app.version`, `atomic.app.storage.api`, and `atomic.app.oauth.redirect`. Use `atomic.app` when you want the convenience bundle on purpose.

Prerequisites:

- Version API needs JPA datasource and `service_version` table.
- Image API needs JPA datasource + `image` table + storage beans (`ImageService`, `storageClients`).
- if `atomic.app.image.enabled=true` and image/storage beans are missing, app startup can fail (not just API skipped).
- OAuth redirect API needs `OauthServiceProvider` and `OauthStateManager` beans.
- app module controllers now include built-in `HttpStatusException` mapping for their documented wire contract.
- if your host app wants a different app-module error envelope, register a higher-precedence `@RestControllerAdvice`.
- OAuth relay store default is `entity`, so default setup also needs `DataSource`, `PlatformTransactionManager`, and `ObjectMapper`.
- with default `store.type=entity` + `store.fail-fast=true`, missing dependencies fail startup.
- when `store.type=in-memory` or `store.type=cache`, entity(db) dependency validation is skipped.
- if your service uses only in-memory/cache relay and has no datasource, disable JDBC auto-config or provide datasource config.
  - example: `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`
- if `store.type=entity` (default), prepare relay table (`atomic_oauth_relay_code` or configured table-name) before rollout.
- official PostgreSQL starting-point SQL assets ship in module resources:
  - `atomic-app/version`: `META-INF/atomic/sql/postgresql/service_version.sql`
  - `atomic-app/storage-api`: `META-INF/atomic/sql/postgresql/image.sql`
  - `atomic-app/oauth-redirect`: `META-INF/atomic/sql/postgresql/atomic_oauth_relay_code.sql`
- `service_version` and `image` physical table/column names are now fixed in code to match those shipped SQL assets.
- Login API should consume relay payload using `ConsumeOauthRelayCodeUseCase.consume(relayCode)`.
- Supported relay seams are the exported build/issue/consume use-case beans plus the exported `OauthRelayCodeStore` seam.

### 6) Heartbeat module (`atomic.heartbeat`)

- `provider.type=custom`: register `HeartbeatProvider` bean explicitly.
- `dedup.leader.backend=custom`: register `LeaderElector` bean explicitly.
- `dedup.mode=leader` + `backend=jdbc`: provision lock table before rollout (recommended), keep `auto-create-table=false` in production.

Image uploader identity option (without security coupling):

- `atomic.app.image.uploader-parameter-enabled=true` enables uploader parameter enforcement.
- `atomic.app.image.uploader-parameter-name` defines which request parameter to use (for example `memberId`).
- `atomic.app.image.thumbnail-enabled=true` enables thumbnail generation by default; upload requests can override with `thumbnailEnabled=true|false`.
- upload stores that value in persisted image metadata (`image.uploader_id`).
- delete requires same parameter value and rejects mismatch (`403`).
- delete reserves metadata as `DELETE_PENDING`, deletes storage, and purges metadata only after storage cleanup succeeds.
- host apps can inspect lingering `DELETE_PENDING` rows via `InspectDeletePendingImagesUseCase.inspectDeletePendingImages()` and recover them via `RecoverDeletePendingImagesUseCase.recoverDeletePendingImages(limit)` from their own admin job or scheduler. The library does not ship a built-in reaper.
- when enabled in production, align `image` table with nullable `uploader_id` column.

OAuth relay option (without token in callback query):

- `atomic.app.oauth.redirect.enabled=true` enables redirect/callback endpoints.
- callback redirects frontend with `relayCode` only (no raw `id_token`/`access_token` in URL).
- login API consumes relay payload via `ConsumeOauthRelayCodeUseCase.consume(relayCode)`.
- the relay module does not issue your app session or JWT for you; treat `relayCode` consumption as an input to your own login flow.
- internally, oauth redirect now consumes typed OAuth state claims from `atomic.spring.oauth2` and translates them into an application-owned verified-state model before callback use-cases read redirect URI, nonce, or callback-binding attributes.
- for mobile/desktop clients, the intended path is system browser login -> server callback -> allowlisted app URI/deep link with `relayCode`.
- supported client handoff patterns:
  - web: browser -> server callback -> `https://frontend.example.com/...?...relayCode=...`
  - mobile: system browser / Custom Tabs / SFSafariViewController -> server callback -> allowlisted deep link or app link such as `myapp://oauth/...?...relayCode=...`
  - desktop: system browser -> server callback -> allowlisted loopback URI (`http://127.0.0.1:{port}/...`) or desktop custom scheme
  - desktop loopback support assumes a fixed, pre-allowlisted listener port in this line; random ephemeral callback ports are not matched by the current allowlist policy
  - `redirectTargetType` logging is URI-shape based, so verified app/universal links that still use `https://...` are logged in the same `WEB` bucket as ordinary web redirects
- concrete relay consumption pattern:
  - web frontend receives `relayCode` and posts it to your backend login API
  - mobile/desktop client receives `relayCode` through deep link, app link, loopback, or custom scheme and posts it to your backend login API
  - backend calls `ConsumeOauthRelayCodeUseCase.consume(relayCode)` and then issues your own session/JWT/cookie
- redirect endpoint input `redirectUri` must be an absolute URI and must not include user-info.
- `allowed-redirect-uri-prefixes` is required when redirect API is enabled.
  - matching uses scheme/host/port/path-prefix boundary (not raw string startsWith).
  - each entry must be an absolute URI without query/fragment.
  - invalid entry format fails startup.
  - example: `https://app.example.com/oauth` allows `https://app.example.com/oauth/callback` but rejects `https://app.example.com.evil.com/...`.
  - custom mobile deep links are supported too, but keep the configured entry in the exact URI shape your client emits (`myapp://oauth/...` and `myapp:/oauth/...` are different contracts).
- empty `allowed-redirect-uri-prefixes` fails startup (fail-fast).
- callback binding is enabled by default and validates redirect/callback flow using state-attribute + cookie token match.
  - hardened defaults require `cookie-name` with `__Host-` prefix, `cookie-secure=true`, and `cookie-path=/`.
  - local plain HTTP callbacks can fail unless you use HTTPS or set `atomic.app.oauth.redirect.callback-binding.mode=disabled` for local-only testing (legacy `callback-binding.enabled=false` still works).
  - default `strict` mode clears the callback-binding cookie immediately.
  - `relaxed` mode preserves the cookie after success for multi-tab/back-navigation-friendly UX.
- relay store type default is `entity` (`atomic.app.oauth.redirect.store.type=entity`).
- selected relay store dependencies are validated (`in-memory`/`cache`/`entity`); unselected store dependencies are not validated.
- global relay settings (for example `relay-code-ttl-seconds`) are validated regardless of store type.
- provider callback path mapping:
  - Google/Kakao: `https://{host}{callback-endpoint-path}/{provider}`
  - Apple: `https://{host}{callback-endpoint-path}/apple` (`POST`, `form_post`)
- when Spring Security is enabled, explicitly allow callback/redirect paths and review CSRF policy for Apple `POST` callback path.
  - typical setup: `permitAll` on redirect/callback endpoints + CSRF ignore rule for `{callback-endpoint-path}/apple`
- `GET {callback-endpoint-path}/apple` is rejected with `400` (Apple callback supports `POST` only).
- keep provider `server-redirect-uri` registration aligned with mapping above.
- `cache` uses Spring `CacheManager` (for example Redis cache), `entity` uses datasource/transaction manager.
- `relay-code-ttl-seconds` must be greater than zero (validated at startup).
- `store.type=cache` requires configured `cache-name` to exist in `CacheManager` at startup.
- `store.type=cache` also requires a cache backend that supports atomic remove-and-return consume for relay payloads.
- `store.type=entity` table name allows only letters, numbers, and underscores.
- cache/entity stores validate expiration on consume (`pop`) and remove consumed relay data.
- for cache backends, configure backend TTL/eviction to avoid stale expired keys accumulating.
- for entity store, run periodic cleanup (for example `DELETE FROM atomic_oauth_relay_code WHERE expires_at <= NOW()`) for unconsumed expired rows.
- callback/state validation errors are wrapped as `HttpStatusException(400)` in app oauth redirect service.
- upstream provider I/O errors can propagate as `HttpStatusException(500)` from oauth module.
- app module controllers map `HttpStatusException` to the documented HTTP status and `BaseResponse.error(...)` envelope by default.
- with `store.fail-fast=false`, selected store errors (missing deps, invalid cache-name/ttl, unavailable cache, unsupported atomic cache backend) do not fail startup and fall back to in-memory store.
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
- production one-time state policy should be explicit: either custom/shared state store path, or (single-node only) in-memory state store with clear operational limits.

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
  - `checkAppVersionUseCase`, `appVersionController`
  - `issueOauthRelayCodeUseCase`, `consumeOauthRelayCodeUseCase`, `oauthRelayCodeStore`, `appOauthRedirectController`
  - `appImageApiService`, `appStorageController`

For oauth redirect specifically, treat the exported build/issue/consume use-case beans,
`OauthRelayCodeStore`, and the web adapter beans as the supported host seams. Internal
composition/support types may exist in the context, but host apps should not customize those
directly.

## What Was Ambiguous Before (Review Summary)

Before this README rewrite, the main ambiguity points were:

1. `atomic-starter` alone looks sufficient, but feature modules must be added explicitly.
2. Module-specific mandatory properties were spread across separate docs.
3. App-side required components (`LogSaver`, `ApiLogAspect`, `SecurityFilterChain`, callback controller) were not visible in one place.
4. Property setup examples were missing from root-level onboarding.

This README now consolidates those points into one starter-first onboarding path.

## Detailed Guides

- [Atomic Quick Start](docs/usage/quick-start.md)
- [Advanced Operations Playbook](docs/usage/advanced-playbook.md)
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
- [Property Reference by Module](docs/usage/environment-variables.md)

---
Developed with Codex.
