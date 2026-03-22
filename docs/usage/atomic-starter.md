# atomic.starter Guide

## Why Use This Module

Use `atomic.starter` to reduce boilerplate bean registration.

- auto-configuration activates only when corresponding atomic module is on classpath
- `atomic.app` is not bundled in starter (must be added separately)
- add `atomic.contract` when app code directly uses `BaseResponse` / `HttpStatusException`
- you still choose feature modules explicitly (`app`, `storage`, `spring.web`, `spring.idempotency`, `spring.security`, `spring.oauth2`, `heartbeat`)
- heavy dependencies are not forced unless you add that module

## Dependency Pattern

Published artifact examples (`v0.0.4`):

```kotlin
dependencies {
  implementation("com.infosung:atomic.contract:0.0.4")
  implementation("com.infosung:atomic.storage:0.0.4")
  implementation("com.infosung:atomic.spring.web:0.0.4")
  implementation("com.infosung:atomic.spring.security:0.0.4")
  implementation("com.infosung:atomic.spring.idempotency:0.0.4")
  implementation("com.infosung:atomic.spring.oauth2:0.0.4")
  implementation("com.infosung:atomic.heartbeat:0.0.4")
  implementation("com.infosung:atomic.starter:0.0.4")
  implementation("com.infosung:atomic.app:0.0.4")
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

Local multi-module usage is still valid when you need source-level customization.

Local multi-module:

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

`atomic.app` notes:

- `atomic.app.version` API can run with JPA alone.
- `atomic.app.image` API needs storage beans (`ImageService`, `storageClients`), typically from `atomic.starter` + `atomic.storage`.
- `atomic.app.oauth.redirect` API needs oauth beans (`OauthServiceProvider`, `OauthStateManager`), typically from `atomic.starter` + `atomic.spring.oauth2`.

## What Gets Auto-Configured

### contract

- `TimeProvider`
- `TraceIdGenerator`

### storage

When `atomic.storage` exists and `atomic.storage.enabled=true`:

- `Map<String, StorageClient>` bean name: `storageClients`
- `Map<String, StorageProfile>` bean name: `storageProfiles`
- `ImageService`
- backend `type` supports `s3`, `r2`, `minio` (all use S3-compatible client path)
- if all backend entries are disabled/missing, maps can be empty and storage API fails at request time (unknown storage type).

### spring.web

When `atomic.spring.web` exists and `atomic.web.enabled=true` (default `true`):

- `JsonTransfer`
- `ServiceLogger` (only if `LogSaver` bean exists)
- `ApiLogFilter` + filter registration (only if `ServiceLogger` exists)
- `RateLimitStore` + `RateLimitPolicyResolver` + `RateLimitKeyResolver` + `RateLimitFilter` (when `atomic.web.rate-limit.enabled=true`)

Still required from app:

- for logging/exception mapping features: `LogSaver`, `ApiLogAspect`, `BaseExceptionHandler`
- for rate-limit only: no mandatory app bean
- for explicit custom mode, set `atomic.web.rate-limit.store=custom` and register `RateLimitStore`

Rate-limit store selection:

- `auto` (default): use Redis store when `StringRedisTemplate` bean exists, else in-memory.
- `in-memory`: always process-local in-memory store.
- `redis`: requires `StringRedisTemplate` bean.
- `custom`: requires user-defined `RateLimitStore` bean.

### spring.idempotency

When `atomic.spring.idempotency` exists and `atomic.idempotency.enabled=true`:

- `IdempotencyStore` (default in-memory when missing)
- `IdempotencyFingerprintResolver` (default request-metadata based when missing)
- `IdempotencyFilter` + filter registration

Still required from app (production recommended):

- custom shared `IdempotencyStore` for multi-instance deployment
- optional custom `IdempotencyFingerprintResolver` when key reuse must include body-aware matching

### spring.security

When `atomic.spring.security` exists and `atomic.security.enabled=true` (default `true`):

- `SecurityCookiePolicy`
- `ClientChannelResolver` (default)
- `JwtProvider` (when `atomic.security.jwt.access-key` and `refresh-key` are set)
- `JwtSecurityConfigurerAdapter`

Still required from app:

- your `SecurityFilterChain` configuration that applies `JwtSecurityConfigurerAdapter`
- `atomic.security.jwt.access-key` and `atomic.security.jwt.refresh-key` when JWT helper auto-config is enabled
- current behavior: when `ObjectMapper` is available, `JwtSecurityConfigurerAdapter` requires `JwtProvider` (auto or custom).
  - if `atomic.security.enabled=true` and provider is missing, startup fails (fail-fast).
  - if `ObjectMapper` is missing, `JwtSecurityConfigurerAdapter` is not auto-registered.
  - if `atomic.security.jwt.enabled=false`, register custom `JwtProvider` or disable security auto-config.

### spring.oauth2

When `atomic.spring.oauth2` exists and `atomic.oauth2.enabled=true` (default `true`):

- shared `RestClient` bean name: `atomicOauthRestClient`
- `OauthStateStore` (when explicitly enabled, or when custom store bean is provided)
- `OauthStateManager` (when `atomic.oauth2.state.signing-secret` is set)
- `OauthServiceProvider`
- provider beans from properties:
  - single-client provider bean, or
  - multi-client routed provider bean (`web/android/ios` style)

Still required from app:

- callback controller / redirect handling
- optional provider customization beans (if you need non-default verifier/parser/client behavior)

### heartbeat

When `atomic.heartbeat` exists and `atomic.heartbeat.enabled=true`:

- `HeartbeatProvider` (default HTTP provider using healthchecks-style endpoint mapping)
- dedup policy mode (`none`, `leader`, `per-instance`) and leader backend (`redis`, `jdbc`, `custom`)
- `HeartbeatOrchestrator` (init/destroy lifecycle managed)
- optional DB/Redis dependency checks with per-check intervals
- `provider.type=custom` requires custom `HeartbeatProvider` bean (missing bean fails startup)
- `dedup.leader.backend=custom` requires custom `LeaderElector` bean (missing bean fails startup)
- in `leader + jdbc`, default `auto-create-table=false` means lock table should be migration-managed in production
- see [atomic.heartbeat Guide](atomic-heartbeat.md) for property reference and behavior details

OAuth provider beans from properties are registered when:

- `OauthStateManager` is available
  - auto path: `atomic.oauth2.state.enabled=true` (default `true`) and `atomic.oauth2.state.signing-secret` configured
  - or custom `OauthStateManager` bean
- each provider `enabled=true`

When multiple clients are configured for one provider:

- route key is stored in OAuth state attribute (`route-attribute-key`, default: `atomicClientKey`)
- `default-client-key` is required
- `exchangeCode` requires route key in signed state and selects client from that value
- `refreshToken`/`revokeToken`/`resolveIdentity` can select client by `additionalParameters[route-attribute-key]`
- `resolveIdentity` can also auto-route by `audience`; if not provided, router may probe id-token validation across clients
- each client must use unique id-token audience values (no overlap between clients)

State handling notes:

- `exchangeCode` already verifies state internally.
- if one-time store is enabled, `verifyState(...)` consumes state entry; call `readState(...)` when you only need to inspect claims.
- `atomic.oauth2.state.in-memory-store.enabled` default is `false`.
- reason: in-memory one-time state store is process-local, so multi-instance environments can fail callback validation.
- enable in-memory store only for local/single-instance setups, or provide a shared/distributed `OauthStateStore` for production.

## OAuth Provider Property Notes

### Google

- required (single-client mode):
  - `client-id`
  - `client-secret`
  - `server-redirect-uri`
- required (multi-client mode):
  - `clients.{clientKey}.client-id`
  - `clients.{clientKey}.client-secret`
  - `clients.{clientKey}.server-redirect-uri`
- optional:
  - `default-client-key` (required in multi-client mode)
  - `route-attribute-key` (default: `atomicClientKey`)
  - `allowed-audiences` / `clients.{clientKey}.allowed-audiences` (empty -> defaults to client id)
  - `supported-scopes` (empty -> no whitelist)
  - `verifier-issuers` (default includes `https://accounts.google.com`, `accounts.google.com`)
  - `require-nonce-validation` (default: `false`)

### Kakao

- required (single-client mode):
  - `client-id`
  - `server-redirect-uri`
- required (multi-client mode):
  - `clients.{clientKey}.client-id`
  - `clients.{clientKey}.server-redirect-uri`
- optional:
  - `default-client-key` (required in multi-client mode)
  - `route-attribute-key` (default: `atomicClientKey`)
  - `client-secret` / `clients.{clientKey}.client-secret`
  - `supported-scopes` (empty -> no whitelist)
  - `require-nonce-validation` (default: `true`)
  - `id-token-issuer` (default `https://kauth.kakao.com`)
  - `id-token-jwk-set-uri` (default `https://kauth.kakao.com/.well-known/jwks.json`)
  - `id-token-allowed-audiences` / `clients.{clientKey}.id-token-allowed-audiences` (empty -> defaults to client id)

### Apple

- required (single-client mode):
  - `client-id`
  - `server-redirect-uri`
- required (multi-client mode):
  - `clients.{clientKey}.client-id`
  - `clients.{clientKey}.server-redirect-uri`
- optional:
  - `default-client-key` (required in multi-client mode)
  - `route-attribute-key` (default: `atomicClientKey`)
  - `require-nonce-validation` (default: `true`)
  - `id-token-issuer` (default `https://appleid.apple.com`)
  - `id-token-jwk-set-uri` (default `https://appleid.apple.com/auth/keys`)
  - `id-token-allowed-audiences` / `clients.{clientKey}.id-token-allowed-audiences` (empty -> defaults to client id)

## Minimal Properties Example

Complete property list by module is maintained in: [Property Reference by Module](environment-variables.md)

```yaml
atomic:
  storage:
    enabled: true
    backends:
      S3:
        type: s3
        region: ap-northeast-2
        endpoint: https://s3.ap-northeast-2.amazonaws.com
        bucket: my-bucket
        cdn: https://cdn.example.com

  web:
    logging:
      enabled: true
      queue-size: 10000
    rate-limit:
      enabled: true
      store: auto
      limit: 100
      window-seconds: 60
      include-methods: [GET, POST, PUT, PATCH, DELETE]
      exclude-path-prefixes: [/actuator]
      path-key-strategy: rule-prefix
      key-strategy: ip
      ip:
        trust-forwarded-headers: false
      key-header-name: X-User-Id
      missing-key-policy: reject
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
    enabled: true
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
    jwt:
      enabled: true
      access-key: your-access-key
      refresh-key: your-refresh-key
      access-expired-second: 3600
      refresh-expired-second: 1209600

  oauth2:
    state:
      enabled: true
      signing-secret: your-state-signing-secret-at-least-32-bytes # must be >= 32 bytes
      in-memory-store:
        enabled: false
    providers:
      google:
        enabled: true
        default-client-key: web
        route-attribute-key: atomicClientKey
        clients:
          web:
            client-id: your-google-web-client-id
            client-secret: your-google-web-client-secret
            server-redirect-uri: https://api.example.com/oauth/callback/google
          android:
            client-id: your-google-android-client-id
            client-secret: your-google-android-client-secret
            server-redirect-uri: https://api.example.com/oauth/callback/google
          ios:
            client-id: your-google-ios-client-id
            client-secret: your-google-ios-client-secret
            server-redirect-uri: https://api.example.com/oauth/callback/google
      kakao:
        enabled: true
        default-client-key: web
        clients:
          web:
            client-id: your-kakao-web-client-id
            client-secret: your-kakao-web-client-secret
            server-redirect-uri: https://api.example.com/oauth/callback/kakao
          app:
            client-id: your-kakao-app-client-id
            server-redirect-uri: https://api.example.com/oauth/callback/kakao
      apple:
        enabled: true
        default-client-key: web
        clients:
          web:
            client-id: your-apple-web-client-id
            server-redirect-uri: https://api.example.com/oauth/callback/apple
          ios:
            client-id: your-apple-ios-client-id
            server-redirect-uri: https://api.example.com/oauth/callback/apple
```

## OAuth Routing Usage

To select a non-default client for `buildAuthorizationUrl`, include route key in request:

```kotlin
val authUrl =
    provider.buildAuthorizationUrl(
        OauthAuthorizationRequest(
            redirectUri = "myapp://oauth/callback",
            stateAttributes = mapOf("atomicClientKey" to "android"),
        ),
    )
```

For `refreshToken` / `revokeToken` / `resolveIdentity`, pass route key via `additionalParameters`:

```kotlin
provider.refreshToken(
    OauthTokenRefreshRequest(
        refreshToken = refreshToken,
        additionalParameters = mapOf("atomicClientKey" to "ios"),
    ),
)
```

## Important Notes

- `atomic.starter` does not replace domain-specific app configuration.
- If you want full custom behavior, define your own beans; starter beans back off with `@ConditionalOnMissingBean`.
- Configure only modules you actually add to dependencies.
- `atomic.app` APIs are provided by `atomic.app` auto-configuration (not by starter directly).
- Rate-limit rules are evaluated in declaration order (first match wins).
- `atomic.web.rate-limit.path-key-strategy` defaults to `rule-prefix` to avoid path-variable key sharding; use `request-uri` for legacy behavior.
- `path-prefix`/`exclude-path-prefixes` use exact prefix boundary matching (`/api/v1` does not match `/api/v10`).
- Effective rate-limit key is `actor|method|pathKey`; with default `rule-prefix`, unmatched routes use `pathKey=default` and share one bucket.
- `atomic.web.rate-limit.key-strategy=header` rejects missing header keys by default (`missing-key-policy=reject`).
- `atomic.web.rate-limit.key-strategy=ip` uses `remoteAddr` by default. set `atomic.web.rate-limit.ip.trust-forwarded-headers=true` only behind trusted proxy/ingress that rewrites forwarding headers.
- `atomic.web.rate-limit.redis.key-prefix` must be non-blank when store is `redis` or `auto`.
- `X-RateLimit-Reset` and `Retry-After` are seconds until current fixed-window boundary.
- Rate-limit rejection paths now expose stable JSON codes:
  - `RATE_LIMIT_KEY_REQUIRED`
  - `RATE_LIMIT_EXCEEDED`
- Any user-defined `RateLimitStore` bean overrides starter-provided store (`@ConditionalOnMissingBean`), even when `store` is not `custom`.
- `atomic.spring.idempotency` default store is process-local in-memory; for multi-instance services register shared `IdempotencyStore`.
- Replayed idempotent responses set `X-Idempotent-Replay=true`; when cached body is omitted by size limit, `X-Idempotent-Replay-Body-Omitted=true` is added.
- `atomic.idempotency.max-cached-body-bytes` is a hard replay-body capture limit; replay omits non-replayable headers (`Set-Cookie`, hop-by-hop, dynamic server headers).
- `atomic.idempotency.processing-ttl-seconds` controls in-flight lock TTL; `atomic.idempotency.ttl-seconds` controls completed replay entry TTL.
- `Idempotency-Key` should be namespaced per actor/tenant to avoid cross-client collisions on shared endpoints.
- Idempotency rejection paths now expose stable JSON codes:
  - `IDEMPOTENCY_KEY_REQUIRED`
  - `IDEMPOTENCY_REQUEST_PROCESSING`
  - `IDEMPOTENCY_FINGERPRINT_MISMATCH`
- non-5xx idempotent responses (including 4xx) are cached and replayed for the same key; 5xx/exception path removes active key.
- Recommended default chain order is rate-limit first (`-100`) then idempotency (`-50`).
- OAuth provider auto-configuration requires available `OauthStateManager`.
  - auto path: `atomic.oauth2.state.enabled=true` and `atomic.oauth2.state.signing-secret`.
  - custom `OauthStateManager` bean also satisfies this condition.
- Required minimum provider properties:
  - Google: (`client-id`,`client-secret`,`server-redirect-uri`) or `clients.{key}.*`
  - Kakao: (`client-id`,`server-redirect-uri`) or `clients.{key}.*` (`client-secret` optional)
  - Apple: (`client-id`,`server-redirect-uri`) or `clients.{key}.*`
