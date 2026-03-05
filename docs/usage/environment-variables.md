# Property Reference by Module

This document is a code-aligned property index based on `@ConfigurationProperties` in `atomic-starter` and `atomic-app`.

Column meanings:
- `Default`: code default value.
- `Required When`: condition where missing/invalid value blocks startup or feature activation.

## `atomic.storage` (`atomic.storage`)

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.storage.enabled` | `true` | optional | Enables storage auto-configuration. |
| `atomic.storage.backends.<backend>.enabled` | `true` | optional | Enables a backend entry. |
| `atomic.storage.backends.<backend>.type` | `s3` | backend enabled | Backend type. Supported: `s3`, `r2`, `minio`. |
| `atomic.storage.backends.<backend>.bucket` | empty | backend enabled | Physical bucket/container name. |
| `atomic.storage.backends.<backend>.cdn` | empty | backend enabled | Public CDN base URL used for returned URLs. |
| `atomic.storage.backends.<backend>.prepend-bucket-on-object-key` | `false` | optional | Adds `bucket/` prefix to returned object key fields. |
| `atomic.storage.backends.<backend>.region` | empty | backend enabled | S3-compatible region value. |
| `atomic.storage.backends.<backend>.endpoint` | empty | optional | Custom endpoint (for example MinIO/R2). |
| `atomic.storage.backends.<backend>.path-style-access-enabled` | `false` | optional | Enables path-style access in S3 SDK config. |
| `atomic.storage.backends.<backend>.access-key-id` | empty | pair rule | Static credential access key. Must be paired with secret key. |
| `atomic.storage.backends.<backend>.secret-access-key` | empty | pair rule | Static credential secret key. Must be paired with access key. |
| `atomic.storage.backends.<backend>.session-token` | empty | optional | Session token for temporary credentials; valid only with access/secret pair. |

Validation notes:
- Enabled backend requires non-blank `bucket`, `cdn`, `region`.
- Static credential partial input is invalid.
- If no backend entry is enabled, storage beans can still be registered as empty maps; storage API then fails at request time with unknown storage type.

## `atomic.spring.web` (`atomic.web`)

### Common / Logging / JSON

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.web.enabled` | `true` | optional | Enables web auto-configuration. |
| `atomic.web.logging.enabled` | `true` | optional | Enables API logging infrastructure. |
| `atomic.web.logging.queue-size` | `10000` | logging enabled | Max in-memory queue size for service logger (`> 0`). |
| `atomic.web.logging.filter.enabled` | `true` | optional | Enables logging filter registration. |
| `atomic.web.logging.filter.order` | `1` | optional | Servlet filter order for API logging filter. |
| `atomic.web.logging.filter.url-patterns` | `/*` | optional | URL patterns for API logging filter. |
| `atomic.web.json.sensitive-key-pattern` | empty | optional | Optional regex for sensitive key masking in JSON logging/transfer paths. |

### Rate Limit

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.web.rate-limit.enabled` | `false` | optional | Enables rate-limit filter registration. |
| `atomic.web.rate-limit.store` | `AUTO` | rate-limit enabled | Store mode: `AUTO`, `IN_MEMORY`, `REDIS`, `CUSTOM`. |
| `atomic.web.rate-limit.limit` | `100` | rate-limit enabled | Default request limit per window (`> 0`). |
| `atomic.web.rate-limit.window-seconds` | `60` | rate-limit enabled | Default fixed-window size (`> 0`). |
| `atomic.web.rate-limit.include-methods` | `GET,POST,PUT,PATCH,DELETE` | rate-limit enabled | Methods subject to throttling (must include at least one non-blank method). |
| `atomic.web.rate-limit.exclude-path-prefixes` | `/actuator` | optional | Path prefixes bypassed by rate-limit. |
| `atomic.web.rate-limit.key-strategy` | `IP` | rate-limit enabled | Actor key strategy: `IP` or `HEADER`. |
| `atomic.web.rate-limit.key-header-name` | `X-User-Id` | key-strategy is `HEADER` | Header name used as actor key (non-blank required). |
| `atomic.web.rate-limit.missing-key-policy` | `REJECT` | key-strategy is `HEADER` | Behavior when key is missing: `REJECT` or `SKIP`. |
| `atomic.web.rate-limit.path-key-strategy` | `RULE_PREFIX` | optional | Path key composition strategy: `RULE_PREFIX` or `REQUEST_URI`. |
| `atomic.web.rate-limit.fail-open` | `true` | optional | If true, lets request pass when store fails. |
| `atomic.web.rate-limit.response-body` | `Too many requests.` | optional | Plain text body for `429` responses. |
| `atomic.web.rate-limit.rules[].path-prefix` | empty | optional | Path prefix matcher for per-rule overrides. |
| `atomic.web.rate-limit.rules[].methods` | empty | optional | Method matcher for per-rule overrides. |
| `atomic.web.rate-limit.rules[].limit` | empty | when set | Per-rule limit override (`> 0`). |
| `atomic.web.rate-limit.rules[].window-seconds` | empty | when set | Per-rule window override (`> 0`). |
| `atomic.web.rate-limit.filter.order` | `-100` | optional | Servlet filter order for rate-limit filter. |
| `atomic.web.rate-limit.filter.url-patterns` | `/*` | optional | URL patterns for rate-limit filter. |
| `atomic.web.rate-limit.redis.key-prefix` | `atomic:ratelimit:` | store `REDIS` or `AUTO` | Redis key prefix (non-blank required in redis-capable modes). |
| `atomic.web.rate-limit.in-memory.cleanup-interval` | `1000` | optional | Cleanup interval in operation count (`>= 0`). |
| `atomic.web.rate-limit.ip.trust-forwarded-headers` | `false` | key-strategy is `IP` | Trusts forwarded headers for client IP extraction. |

## `atomic.spring.idempotency` (`atomic.idempotency`)

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.idempotency.enabled` | `false` | optional | Enables idempotency filter auto-configuration. |
| `atomic.idempotency.header-name` | `Idempotency-Key` | idempotency enabled | Header used to read idempotency key (non-blank). |
| `atomic.idempotency.ttl-seconds` | `300` | idempotency enabled | TTL for completed idempotency records (`> 0`). |
| `atomic.idempotency.processing-ttl-seconds` | `3600` | idempotency enabled | Processing lock TTL while first request is running (`> 0`). |
| `atomic.idempotency.require-header` | `true` | optional | If true, missing key header is rejected on included methods. |
| `atomic.idempotency.include-methods` | `POST` | idempotency enabled | Methods subject to idempotency (at least one non-blank). |
| `atomic.idempotency.fail-open` | `true` | optional | If true, request continues on store failures. |
| `atomic.idempotency.replay-header-name` | `X-Idempotent-Replay` | idempotency enabled | Response header indicating replayed response (non-blank). |
| `atomic.idempotency.replay-body-omitted-header-name` | `X-Idempotent-Replay-Body-Omitted` | idempotency enabled | Header indicating replay body omitted due to size cap (non-blank). |
| `atomic.idempotency.max-cached-body-bytes` | `262144` | optional | Max replay body bytes to cache (`>= 0`). |
| `atomic.idempotency.in-memory.cleanup-interval` | `1000` | optional | Cleanup interval for in-memory store (`>= 0`). |
| `atomic.idempotency.filter.enabled` | `true` | idempotency enabled | Enables servlet filter registration. |
| `atomic.idempotency.filter.order` | `-50` | idempotency filter enabled | Servlet filter order for idempotency filter. |
| `atomic.idempotency.filter.url-patterns` | `/*` | idempotency filter enabled | URL patterns (non-blank list required when filter enabled). |

## `atomic.spring.security` (`atomic.security`)

### Common / JWT / Cookie

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.security.enabled` | `true` | optional | Enables security auto-configuration. |
| `atomic.security.exclude-urls` | empty | optional | Excluded request patterns (`METHOD /path` format). |
| `atomic.security.jwt.enabled` | `true` | optional | Enables auto `JwtProvider` registration path. |
| `atomic.security.jwt.access-key` | empty | auto `JwtProvider` path | Access token signing key (non-blank required). |
| `atomic.security.jwt.refresh-key` | empty | auto `JwtProvider` path | Refresh token signing key (non-blank required). |
| `atomic.security.jwt.algorithm` | `HmacSHA512` | optional | JCA HMAC algorithm name. |
| `atomic.security.jwt.service-name` | `InfosungAtomic` | optional | Issuer/service claim value. |
| `atomic.security.jwt.access-expired-second` | `3600` | optional | Access token expiration seconds. |
| `atomic.security.jwt.refresh-expired-second` | `1209600` | optional | Refresh token expiration seconds. |
| `atomic.security.cookie.same-site` | `Strict` | optional | SameSite policy for security cookies. |
| `atomic.security.cookie.secure` | `true` | optional | Secure flag for security cookies. |
| `atomic.security.cookie.path` | `/` | optional | Cookie path. |
| `atomic.security.cookie.domain` | empty | optional | Cookie domain override. |

Runtime note:
- When `atomic.security.enabled=true` and `ObjectMapper` bean exists, `JwtSecurityConfigurerAdapter` path requires `JwtProvider` (auto or custom).
- If `ObjectMapper` is missing, `JwtSecurityConfigurerAdapter` is not auto-registered.

## `atomic.spring.oauth2` (`atomic.oauth2`)

### Common / State

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.oauth2.enabled` | `true` | optional | Enables oauth2 auto-configuration. |
| `atomic.oauth2.state.enabled` | `true` | optional | Enables state-manager path. |
| `atomic.oauth2.state.signing-secret` | empty | state-manager/provider path | Signing secret for state token (non-blank, >= 32 bytes). |
| `atomic.oauth2.state.issuer` | `atomic-oauth-state` | optional | State token issuer claim. |
| `atomic.oauth2.state.ttl-seconds` | `300` | optional | State token TTL seconds (`> 0`). |
| `atomic.oauth2.state.max-attributes-entry-count` | `10` | optional | Max attribute entry count in state payload. |
| `atomic.oauth2.state.max-attributes-bytes` | `512` | optional | Max encoded bytes for state attributes. |
| `atomic.oauth2.state.max-state-token-length` | `1200` | optional | Max serialized state token length. |
| `atomic.oauth2.state.in-memory-store.enabled` | `false` | optional | Enables in-memory one-time state store. |
| `atomic.oauth2.state.in-memory-store.cleanup-interval` | `100` | in-memory state store enabled | Cleanup interval for in-memory state store (`> 0`). |

Provider registration note:
- Provider beans from properties require an available `OauthStateManager`.
- Auto path requires `atomic.oauth2.state.enabled=true` and non-blank `atomic.oauth2.state.signing-secret`.
- Custom `OauthStateManager` bean also satisfies provider registration condition.

### Google Provider (`atomic.oauth2.providers.google`)

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.oauth2.providers.google.enabled` | `false` | optional | Enables Google provider bean registration. |
| `atomic.oauth2.providers.google.route-attribute-key` | `atomicClientKey` | optional | State attribute key for routed client selection. |
| `atomic.oauth2.providers.google.default-client-key` | empty | multiple google clients configured | Default routed client key. |
| `atomic.oauth2.providers.google.client-id` | empty | google enabled (single-client mode) | Google OAuth client id. |
| `atomic.oauth2.providers.google.client-secret` | empty | google enabled (single-client mode) | Google OAuth client secret. |
| `atomic.oauth2.providers.google.server-redirect-uri` | empty | google enabled (single-client mode) | Server callback redirect URI. |
| `atomic.oauth2.providers.google.authorization-grant-type` | `authorization_code` | optional | OAuth grant type for token exchange. |
| `atomic.oauth2.providers.google.default-scopes` | `openid,email,profile` | optional | Default scopes when request scopes are empty. |
| `atomic.oauth2.providers.google.supported-scopes` | empty | optional | Optional allowlist for requested scopes. |
| `atomic.oauth2.providers.google.user-info-endpoint` | `https://openidconnect.googleapis.com/v1/userinfo` | optional | User info endpoint URL. |
| `atomic.oauth2.providers.google.allowed-audiences` | empty | optional | Allowed id-token audiences (defaults to client id when empty). |
| `atomic.oauth2.providers.google.require-nonce-validation` | `false` | optional | Requires nonce when resolving identity from id token. |
| `atomic.oauth2.providers.google.verifier-issuers` | `https://accounts.google.com,accounts.google.com` | optional | Allowed Google id-token issuers. |
| `atomic.oauth2.providers.google.clients.<clientKey>.client-id` | empty | google enabled + multi-client mode | Client id for routed client. |
| `atomic.oauth2.providers.google.clients.<clientKey>.client-secret` | empty | google enabled + multi-client mode | Client secret for routed client. |
| `atomic.oauth2.providers.google.clients.<clientKey>.server-redirect-uri` | empty | google enabled + multi-client mode | Redirect URI for routed client. |
| `atomic.oauth2.providers.google.clients.<clientKey>.allowed-audiences` | empty | optional | Allowed audiences for routed client. |

### Kakao Provider (`atomic.oauth2.providers.kakao`)

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.oauth2.providers.kakao.enabled` | `false` | optional | Enables Kakao provider bean registration. |
| `atomic.oauth2.providers.kakao.route-attribute-key` | `atomicClientKey` | optional | State attribute key for routed client selection. |
| `atomic.oauth2.providers.kakao.default-client-key` | empty | multiple kakao clients configured | Default routed client key. |
| `atomic.oauth2.providers.kakao.client-id` | empty | kakao enabled (single-client mode) | Kakao OAuth client id. |
| `atomic.oauth2.providers.kakao.client-secret` | empty | optional | Optional Kakao OAuth client secret. |
| `atomic.oauth2.providers.kakao.server-redirect-uri` | empty | kakao enabled (single-client mode) | Server callback redirect URI. |
| `atomic.oauth2.providers.kakao.default-scopes` | `openid` | optional | Default scopes when request scopes are empty. |
| `atomic.oauth2.providers.kakao.supported-scopes` | empty | optional | Optional allowlist for requested scopes. |
| `atomic.oauth2.providers.kakao.user-info-endpoint` | `https://kapi.kakao.com/v1/oidc/userinfo` | optional | User info endpoint URL. |
| `atomic.oauth2.providers.kakao.require-nonce-validation` | `true` | optional | Requires nonce when resolving identity from id token. |
| `atomic.oauth2.providers.kakao.id-token-issuer` | `https://kauth.kakao.com` | optional | Expected issuer for Kakao id token. |
| `atomic.oauth2.providers.kakao.id-token-allowed-audiences` | empty | optional | Allowed Kakao id-token audiences (defaults to client id). |
| `atomic.oauth2.providers.kakao.id-token-jwk-set-uri` | `https://kauth.kakao.com/.well-known/jwks.json` | optional | Kakao JWK set URI. |
| `atomic.oauth2.providers.kakao.clients.<clientKey>.client-id` | empty | kakao enabled + multi-client mode | Client id for routed client. |
| `atomic.oauth2.providers.kakao.clients.<clientKey>.client-secret` | empty | optional | Optional client secret for routed client. |
| `atomic.oauth2.providers.kakao.clients.<clientKey>.server-redirect-uri` | empty | kakao enabled + multi-client mode | Redirect URI for routed client. |
| `atomic.oauth2.providers.kakao.clients.<clientKey>.id-token-allowed-audiences` | empty | optional | Allowed id-token audiences for routed client. |

### Apple Provider (`atomic.oauth2.providers.apple`)

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.oauth2.providers.apple.enabled` | `false` | optional | Enables Apple provider bean registration. |
| `atomic.oauth2.providers.apple.route-attribute-key` | `atomicClientKey` | optional | State attribute key for routed client selection. |
| `atomic.oauth2.providers.apple.default-client-key` | empty | multiple apple clients configured | Default routed client key. |
| `atomic.oauth2.providers.apple.client-id` | empty | apple enabled (single-client mode) | Apple service/client id. |
| `atomic.oauth2.providers.apple.server-redirect-uri` | empty | apple enabled (single-client mode) | Server callback redirect URI. |
| `atomic.oauth2.providers.apple.default-scopes` | `email` | optional | Default scopes when request scopes are empty. |
| `atomic.oauth2.providers.apple.require-nonce-validation` | `true` | optional | Requires nonce when resolving identity from id token. |
| `atomic.oauth2.providers.apple.id-token-issuer` | `https://appleid.apple.com` | optional | Expected issuer for Apple id token. |
| `atomic.oauth2.providers.apple.id-token-allowed-audiences` | empty | optional | Allowed Apple id-token audiences (defaults to client id). |
| `atomic.oauth2.providers.apple.id-token-jwk-set-uri` | `https://appleid.apple.com/auth/keys` | optional | Apple JWK set URI. |
| `atomic.oauth2.providers.apple.clients.<clientKey>.client-id` | empty | apple enabled + multi-client mode | Client id for routed client. |
| `atomic.oauth2.providers.apple.clients.<clientKey>.server-redirect-uri` | empty | apple enabled + multi-client mode | Redirect URI for routed client. |
| `atomic.oauth2.providers.apple.clients.<clientKey>.id-token-allowed-audiences` | empty | optional | Allowed id-token audiences for routed client. |

## `atomic.heartbeat` (`atomic.heartbeat`)

### Core / Ping / Provider

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.heartbeat.enabled` | `false` | optional | Enables heartbeat auto-configuration. |
| `atomic.heartbeat.scheduler-thread-prefix` | `atomic-heartbeat` | optional | Thread name prefix for scheduler/check/leader loops. |
| `atomic.heartbeat.ping.interval` | `30s` | heartbeat enabled | Ping interval (`> 0`). |
| `atomic.heartbeat.ping.send-start-event` | `false` | optional | Sends startup `START` event once (best-effort). |
| `atomic.heartbeat.ping.fail-open` | `true` | optional | Swallows transport failures when true. |
| `atomic.heartbeat.provider.type` | `healthchecks` | optional | Provider mode: `healthchecks` or `custom`. |
| `atomic.heartbeat.provider.timeout` | `2s` | provider type `healthchecks` | HTTP request timeout (`> 0`). |
| `atomic.heartbeat.provider.connect-timeout` | `1s` | provider type `healthchecks` | HTTP connect timeout (`> 0`). |
| `atomic.heartbeat.provider.headers` | empty | optional | Additional HTTP headers for built-in provider. |
| `atomic.heartbeat.provider.instance-id` | `default` | optional | Template variable for `{instanceId}` replacement. |
| `atomic.heartbeat.provider.healthchecks.base-url` | empty | provider type `healthchecks` | Required non-blank base URL. |
| `atomic.heartbeat.provider.healthchecks.success-path` | empty | optional | Success path (or absolute URL). |
| `atomic.heartbeat.provider.healthchecks.fail-path` | `/fail` | optional | Fail path (or absolute URL). |
| `atomic.heartbeat.provider.healthchecks.start-path` | `/start` | optional | Start path (or absolute URL). |

### Dependency Checks

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.heartbeat.checks.missing-bean-policy` | `WARN` | optional | Missing dependency behavior: `WARN` or `FAIL`. |
| `atomic.heartbeat.checks.db.enabled` | `false` | optional | Enables DB dependency check loop. |
| `atomic.heartbeat.checks.db.required` | `true` | db check enabled | Whether DB result gates heartbeat success. |
| `atomic.heartbeat.checks.db.interval` | `30s` | db check enabled | DB check interval (`> 0`). |
| `atomic.heartbeat.checks.db.timeout` | `2s` | db check enabled | DB check timeout (`> 0`). |
| `atomic.heartbeat.checks.db.stale-after` | empty | optional | Stale threshold (`> 0` when set). |
| `atomic.heartbeat.checks.db.query` | `SELECT 1` | db check enabled | Validation query string. |
| `atomic.heartbeat.checks.redis.enabled` | `false` | optional | Enables Redis dependency check loop. |
| `atomic.heartbeat.checks.redis.required` | `true` | redis check enabled | Whether Redis result gates heartbeat success. |
| `atomic.heartbeat.checks.redis.interval` | `30s` | redis check enabled | Redis check interval (`> 0`). |
| `atomic.heartbeat.checks.redis.timeout` | `2s` | redis check enabled | Redis check timeout (`> 0`). |
| `atomic.heartbeat.checks.redis.stale-after` | empty | optional | Stale threshold (`> 0` when set). |

### Dedup / Leader

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.heartbeat.dedup.mode` | `NONE` | optional | Dedup mode: `NONE`, `LEADER`, `PER_INSTANCE`. |
| `atomic.heartbeat.dedup.leader.backend` | `REDIS` | dedup mode `LEADER` | Leader backend: `REDIS`, `JDBC`, `CUSTOM`. |
| `atomic.heartbeat.dedup.leader.owner-id` | empty | optional | Leader owner id; blank auto-generates UUID. |
| `atomic.heartbeat.dedup.leader.lease-duration` | `45s` | dedup mode `LEADER` | Lease TTL (`> 0`). |
| `atomic.heartbeat.dedup.leader.renew-interval` | `15s` | dedup mode `LEADER` | Renew interval (`> 0`, must be `< lease-duration`). |
| `atomic.heartbeat.dedup.leader.redis.key` | `atomic:heartbeat:leader` | leader backend `REDIS` | Redis lock key (non-blank). |
| `atomic.heartbeat.dedup.leader.jdbc.table-name` | `atomic_heartbeat_leader` | leader backend `JDBC` | JDBC lock table name (`[A-Za-z0-9_]+`). |
| `atomic.heartbeat.dedup.leader.jdbc.lock-name` | `default` | leader backend `JDBC` | Lock row name (non-blank). |
| `atomic.heartbeat.dedup.leader.jdbc.auto-create-table` | `false` | leader backend `JDBC` | Runtime auto-create lock table toggle. |

## `atomic.app.version` (`atomic.app.version`)

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.app.version.enabled` | `false` | optional | Enables common version-check API. |
| `atomic.app.version.default-store-url` | `https://www.infosung.com` | optional | Fallback store URL for force-update responses. |
| `atomic.app.version.endpoint-path` | `/api/v1/version/check` | optional | HTTP endpoint path for version-check API. |

## `atomic.app.image` (`atomic.app.image`)

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.app.image.enabled` | `false` | optional | Enables common image upload/delete API. |
| `atomic.app.image.endpoint-path` | `/api/v1/storage/image` | optional | HTTP endpoint base path for image API. |
| `atomic.app.image.default-quality` | `1.0` | image API enabled | Default quality when request omits quality value. |
| `atomic.app.image.min-quality` | `0.1` | image API enabled | Minimum allowed quality. |
| `atomic.app.image.max-quality` | `1.0` | image API enabled | Maximum allowed quality. |
| `atomic.app.image.uploader-parameter-enabled` | `false` | optional | Enables uploader identity parameter validation/storage. |
| `atomic.app.image.uploader-parameter-name` | `uploaderId` | uploader parameter enabled | Request parameter key for uploader identity. |

## `atomic.app.oauth.redirect` (`atomic.app.oauth.redirect`)

### Core / Store

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.app.oauth.redirect.enabled` | `false` | optional | Enables app OAuth redirect/callback relay API. |
| `atomic.app.oauth.redirect.redirect-endpoint-path` | `/oauth/redirect` | optional | Endpoint base path for redirect API. |
| `atomic.app.oauth.redirect.callback-endpoint-path` | `/oauth/callback` | optional | Endpoint base path for callback APIs. |
| `atomic.app.oauth.redirect.relay-code-query-parameter-name` | `relayCode` | optional | Query key appended to client redirect URL. |
| `atomic.app.oauth.redirect.relay-code-ttl-seconds` | `300` | redirect API enabled | Relay code TTL seconds (`> 0`). |
| `atomic.app.oauth.redirect.allowed-redirect-uri-prefixes` | empty | optional | Allowed redirect URI prefix list for open-redirect protection. |
| `atomic.app.oauth.redirect.store.type` | `ENTITY` | optional | Relay store backend: `IN_MEMORY`, `CACHE`, `ENTITY`. |
| `atomic.app.oauth.redirect.store.fail-fast` | `true` | optional | Fails startup on selected-store dependency issues when true. |

### In-Memory Store

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.app.oauth.redirect.store.in-memory.cleanup-interval` | `100` | store type `IN_MEMORY` | Cleanup interval (`<= 0` disables periodic cleanup). |

### Cache Store

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.app.oauth.redirect.store.cache.cache-name` | `atomicOauthRelayCode` | store type `CACHE` | Cache name expected in `CacheManager`. |
| `atomic.app.oauth.redirect.store.cache.key-prefix` | `atomic:oauth:relay:` | store type `CACHE` | Cache key prefix for relay entries. |
| `atomic.app.oauth.redirect.store.cache.ttl-seconds` | empty | optional | Optional cache TTL override (`> 0` when set). |

### Entity Store

| Property | Default | Required When | Description |
|---|---|---|---|
| `atomic.app.oauth.redirect.store.entity.table-name` | `atomic_oauth_relay_code` | store type `ENTITY` | Relay table name (`[A-Za-z0-9_]+`). |
