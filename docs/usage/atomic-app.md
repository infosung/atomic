# atomic.app Guide

## Why Use This Module

Use `atomic.app` when you want prebuilt application APIs instead of composing every controller/service manually.

Current bundled APIs:

- app version check API
- image upload/delete API
- oauth redirect/callback relay API

Internally, `atomic.app` is a bundle of:

- `atomic-app:app-version`
- `atomic-app:storage-api`
- `atomic-app:oauth-redirect`

## Dependency Pattern

`atomic.app` and `atomic.starter` are independent modules.

- `atomic.starter` does not include `atomic.app`.
- `atomic.app` does not require `atomic.starter` for version API.
- `atomic.app` image API requires storage beans, so starter-based setups usually add both.

Recommended (starter-based):

```kotlin
dependencies {
  implementation("com.infosung:atomic.starter:0.0.1")
  implementation("com.infosung:atomic.contract:0.0.1")
  implementation("com.infosung:atomic.app:0.0.1")
}
```

Local multi-module:

```kotlin
dependencies {
  implementation(project(":atomic-starter"))
  implementation(project(":atomic-contract"))
  implementation(project(":atomic-app"))
}
```

Note:

- `atomic.app.version` can work with JPA/datasource only.
- `atomic.app.image` requires storage beans (`ImageService`, `storageClients`) and JPA.
- `atomic.app.oauth.redirect` requires `OauthServiceProvider` + `OauthStateManager` beans (typically from `atomic.starter` + `atomic.spring.oauth2`).
- OAuth relay store default is `entity`, so default setup also needs `DataSource` + `PlatformTransactionManager` + `ObjectMapper`.
- when `store.type=in-memory` or `store.type=cache`, entity(db) dependency validation is skipped.
- if you use only in-memory/cache relay and do not provide datasource, disable JDBC auto-config or provide datasource config.
  - example: `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`
- the easiest path for image API is `atomic.starter` + `atomic.storage` properties.

## Properties

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
          - https://admin.example.com
```

All app APIs are disabled by default. Set each `enabled=true` explicitly.

## Version API

Default endpoint:

- `GET /api/v1/version/check`

Input resolution:

- service: `X-Service-Name` header (required)
- platform: `X-Platform` header (required)
- appVersion: `X-App-Version` header (required)

Response fields:

- `currentVersion`: latest registered version for `(service, platform)`
- `userVersion`: matched client version
- `requiredUpdate`: whether any higher `requireUpdate=true` policy exists
- `storeUrl`: forced-update target URL or `default-store-url`

Expected version policy table:

- table name: `service_version`
- entity fields used: `mainVersion`, `minorVersion`, `patchNumber`, `requireUpdate`, `platform`, `service`, `storeUrl`
- physical column names follow your JPA naming strategy (for Spring default, typically `main_version`, `minor_version`, `patch_number`, `require_update`, `store_url`)

Version API exception semantics:

- `400` when required input is missing or `appVersion` format is invalid (`x.y.z`)
- `400` when client version is not registered in policy rows
- `404` when no policy rows exist for `(service, platform)`

## Image API

Default base endpoint:

- `/api/v1/storage/image`

Endpoints:

- `POST /api/v1/storage/image/{service}/{storageService}` (`multipart/form-data`)
- `DELETE /api/v1/storage/image/{service}/{storageService}?imageId={uuid}`

> **Important (Spring Security)**
> If your service uses Spring Security, you must explicitly include the storage API path in your security rules.
> Add `POST/DELETE /api/v1/storage/image/**` (or your custom `atomic.app.image.endpoint-path/**`) to authenticated/authorized matchers.
> If this path is not included, your uploader-identity checks can be bypassed by security misconfiguration.

POST parameters:

- `file` (required multipart part)
- `quality` (optional query; default `default-quality`; allowed range `min-quality..max-quality`)
- uploader identity parameter (optional by default):
  - enabled when `atomic.app.image.uploader-parameter-enabled=true`
  - parameter name comes from `atomic.app.image.uploader-parameter-name`
  - value is stored into `ImageEntity.uploaderId`

POST response:

- persisted `ImageEntity` metadata (id, bucket, file names, urls, dimensions, sizes, status)

DELETE behavior:

- validates `imageId` UUID format
- validates that row matches `{service}` and `{storageService}`
- when uploader tracking is enabled, validates request uploader parameter equals stored `ImageEntity.uploaderId`
- deletes original/thumbnail objects from resolved storage client
- deletes metadata row

Storage client resolution:

- tries keys in order: `service:storageService`, `service::storageService`, `storageService`
- for each key, also tries exact/upper/lower variants
- if no match, returns `400`

Image API exception semantics:

- `400` invalid quality / unknown storage key / invalid UUID / path mismatch
- `400` uploader parameter missing when uploader tracking is enabled
- `404` image row not found
- `403` uploader mismatch when uploader tracking is enabled
- other upload/delete exceptions can propagate from underlying storage client or image processing layer

## OAuth Redirect API

Default endpoints:

- `GET /oauth/redirect/{provider}?redirectUri={frontendUri}`
- `GET /oauth/callback/{provider}?code=...&state=...` (google/kakao)
- `POST /oauth/callback/apple` (`application/x-www-form-urlencoded`, `state`, `id_token`, optional `code`, optional `user`)
- Google/Kakao provider `server-redirect-uri` must match `https://{host}{callback-endpoint-path}/{provider}`.
- Apple provider callback must match `https://{host}{callback-endpoint-path}/apple` (`POST`, `form_post`).

Behavior:

- callback does not append raw token to URL.
- callback appends only `relayCode` to frontend redirect URI.
- frontend sends `relayCode` to your login API.
- login API consumes relay payload using `AppOauthRelayCodeService.consumeRelayCode(relayCode)`.
- redirect endpoint input `redirectUri` must be an absolute URI and must not include user-info.
- relay store default type is `entity`.
- selected store dependencies are validated; unselected store dependencies are ignored.
- global relay settings (for example `relay-code-ttl-seconds`) are validated regardless of store type.
- `relay-code-ttl-seconds` must be greater than zero (validated at startup).

Relay payload:

- provider name
- `idToken` / `accessToken` / `refreshToken` (when present)
- token metadata (`expiresInSeconds`, scopes, raw payload, nonce, state attributes)

Provider callback differences:

- Google/Kakao: `code/state` query callback -> provider `exchangeCode(...)`
- Apple: `form_post` callback -> `id_token` is received directly and stored in relay payload
- Apple callback accepts optional `user` and stores it in relay raw payload.
- Additional callback parameters are merged into relay raw payload for Apple callback.
- For Google/Kakao callback, additional parameters are forwarded to token exchange request but not guaranteed in relay raw payload.
- Apple `GET {callback-endpoint-path}/apple` is rejected with `400` (use `POST` only).

OAuth redirect exception semantics:

- `400` unsupported provider
- `400` invalid/missing redirectUri
- `400` invalid callback request/state
- `400` relayCode is invalid/expired/already consumed (on consume API call)
- OAuth callback/state errors from oauth module are mapped to `HttpStatusException(400)` in app oauth redirect service.
- Status mapping above assumes your app maps `HttpStatusException` to HTTP response status (for example via `BaseExceptionHandler`).

Security notes:

- Explicitly configure security rules for callback/redirect paths.
- Most services keep redirect/callback endpoints public and enforce authentication at login API.
- Always configure `allowed-redirect-uri-prefixes` in production to prevent open redirect.
  - match uses scheme/host/port/path-prefix boundary (not raw string startsWith).
  - each entry must be an absolute URI without query/fragment.
  - `https://app.example.com/oauth` allows `https://app.example.com/oauth/callback` but rejects `https://app.example.com.evil.com/...`.
  - if this list is empty, any absolute `redirectUri` is accepted.

Relay store notes:

- `store.type=in-memory`: no datasource/cache validation.
- `store.type=cache`: validates cache dependencies only (`CacheManager`, `ObjectMapper`).
  - configured `cache-name` must exist in `CacheManager` at startup.
- `store.type=entity` (default): validates db dependencies only (`DataSource`, `PlatformTransactionManager`, `ObjectMapper`).
  - `table-name` allows only letters, numbers, and underscores.
- when `store.fail-fast=false`, selected store errors (missing deps, invalid cache-name/ttl, unavailable cache) do not fail startup and fall back to in-memory store.
- in-memory fallback is process-local per instance and can break one-time relay semantics in multi-instance deployments.
- entity store expects table columns:
  - `relay_code` (PK, string)
  - `payload_json` (text/json string)
  - `expires_at` (timestamp)
  - `created_at` (timestamp)
- cache/entity stores validate expiration on consume (`pop`) and remove consumed relay data.
- for cache backends, configure backend TTL/eviction policy to avoid stale expired keys accumulating.
- for entity store, run periodic cleanup (for example `DELETE FROM atomic_oauth_relay_code WHERE expires_at <= NOW()`) to remove unconsumed expired rows.
- `in-memory.cleanup-interval <= 0` disables periodic expired-entry cleanup.

## Operational Checklist

- Prepare database schema for `service_version` and `image` tables before enabling APIs.
- if uploader tracking is enabled, add nullable `uploader_id` column to `image` table (or rely on JPA schema generation in non-production environments).
- choose uploader parameter name per service (for example `memberId`, `userKey`, `ownerId`) and configure `atomic.app.image.uploader-parameter-name`.
- for OAuth relay, set `atomic.app.oauth.redirect.allowed-redirect-uri-prefixes` before production rollout.
- if `store.type=entity` (default), create relay table (`atomic_oauth_relay_code` or configured table-name) before rollout.
- if `store.type=entity`, schedule expired-row cleanup for unconsumed relay entries.
- Configure `atomic.storage.backends.*` before enabling image API.
- Keep `storageType` key naming consistent with your `{service}` and `{storageService}` path policy.
- Enforce multipart size/time limits at application layer.
