# atomic.app Guide

## Why Use This Module

Use `atomic.app` when you want prebuilt application APIs instead of composing every controller/service manually.

For new adoption, prefer the narrowest published module first:

- `atomic.app.version`
- `atomic.app.storage.api`
- `atomic.app.oauth.redirect`

Use `atomic.app` when you intentionally want the convenience bundle that re-exports all three app APIs.

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
- `atomic.app.version` does not require `atomic.starter` for version API.
- `atomic.app.storage.api` requires storage beans, so starter-based setups usually add both.
- `atomic.app.oauth.redirect` usually pairs with `atomic.starter` + `atomic.spring.oauth2`.

Published artifact set (`v0.1.3`):

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

`atomic.app` and its submodules are now part of Maven Central publish scope.
Local multi-module adoption is still useful for source-level customization.

Local multi-module:

```kotlin
dependencies {
  implementation(project(":atomic-starter"))
  implementation(project(":atomic-contract"))
  implementation(project(":atomic-app"))
}
```

Example narrow-module selection (pick only the APIs you actually use):

```kotlin
dependencies {
  implementation(project(":atomic-app:app-version"))
  implementation(project(":atomic-app:storage-api"))
  implementation(project(":atomic-app:oauth-redirect"))
}
```

Published-artifact narrow-module selection:

```kotlin
dependencies {
  implementation("com.infosung:atomic.app.version:0.1.3")
  implementation("com.infosung:atomic.app.storage.api:0.1.3")
  implementation("com.infosung:atomic.app.oauth.redirect:0.1.3")
}
```

Note:

- the two snippets above show all narrow modules side by side as a reference map. In real adoption, pick only the APIs your app actually needs.
- `atomic.app.version` can work with JPA/datasource only.
- `atomic.app.image` requires storage beans (`ImageService`, `storageClients`) and JPA.
- if `atomic.app.image.enabled=true` and required image/storage beans are missing, startup can fail (not only API skipped).
- `atomic.app.oauth.redirect` requires `OauthServiceProvider` + `OauthStateManager` beans (typically from `atomic.starter` + `atomic.spring.oauth2`).
- the `OauthStateManager` used by oauth redirect must be backed by an `OauthStateStore` to preserve one-time callback state semantics.
- OAuth relay store default is `entity`, so default setup also needs `DataSource` + `PlatformTransactionManager` + `ObjectMapper`.
- with default `store.type=entity` + `store.fail-fast=true`, missing dependencies fail startup.
- when `store.type=in-memory` or `store.type=cache`, entity(db) dependency validation is skipped.
- if you use only in-memory/cache relay and do not provide datasource, disable JDBC auto-config or provide datasource config.
  - example: `spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration`
- the easiest path for image API is `atomic.starter` + `atomic.storage` properties.

## Properties

Full property index (default / required condition / description): [Property Reference by Module](environment-variables.md) -> `atomic.app.version`, `atomic.app.image`, `atomic.app.oauth.redirect`

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
      thumbnail-enabled: true
      uploader-parameter-enabled: false
      uploader-parameter-name: uploaderId
    oauth:
      redirect:
        enabled: true
        redirect-endpoint-path: /oauth/redirect
        callback-endpoint-path: /oauth/callback # base path. final callback path is /{provider} or /apple
        relay-code-query-parameter-name: relayCode
        relay-code-ttl-seconds: 300 # must be > 0
        callback-binding:
          enabled: true
          mode: strict # optional: strict, relaxed, disabled. When omitted, legacy enabled flag decides effective mode
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

- `currentVersion`: latest rollout-safe version for `(service, platform)`; this prefers the latest `storeAvailable=true` row and falls back to the latest registered row only when no store-safe row exists
- `userVersion`: matched client version, or the normalized client semver when that version is not explicitly registered
- `requiredUpdate`: the only signal that a force-update target exists for this client version
- `storeUrl`: URL value returned with the response; when no policy URL exists, `default-store-url` can still be used as a fallback

Client contract note:

- decide force-update UX from `requiredUpdate`, not from `storeUrl` presence alone

Expected version policy table:

- table name: `service_version`
- entity fields used: `mainVersion`, `minorVersion`, `patchNumber`, `requireUpdate`, `platform`, `service`, `storeUrl`
- rollout-safe field:
  - `storeAvailable`: whether this row is safe to expose as the current store target and safe to use for forced updates
- semantic-version uniqueness:
  - keep exactly one row per `(service, platform, mainVersion, minorVersion, patchNumber)`
- physical column names are fixed in code and SQL assets:
  - `id`
  - `main_version`
  - `minor_version`
  - `patch_number`
  - `require_update`
  - `store_available`
  - `platform`
  - `service`
  - `store_url`
  - `created_at`

Version API exception semantics:

- stable wire catalog:

| Code | Status | Default message |
|---|---:|---|
| `VERSION_SERVICE_NAME_REQUIRED` | `400` | `Service name is required.` |
| `VERSION_PLATFORM_REQUIRED` | `400` | `Platform is required.` |
| `VERSION_APP_VERSION_REQUIRED` | `400` | `App version is required.` |
| `VERSION_APP_VERSION_FORMAT_INVALID` | `400` | `App version semantic format is invalid.` |
| `VERSION_APP_VERSION_SEGMENT_INVALID` | `400` | `App version segment is invalid.` |
| `VERSION_APP_VERSION_NEGATIVE_INVALID` | `400` | `App version must not contain negative numbers.` |
| `VERSION_INVALID_APP_VERSION` | `400` | `App version is invalid.` |
| `VERSION_POLICY_NOT_FOUND` | `404` | `No version policy was found.` |

- direct use-case integration still receives public version exceptions; the table above describes the default web rendering
- invalid semantic version shape now surfaces through `VERSION_APP_VERSION_FORMAT_INVALID`
- non-numeric semantic version segments now surface through `VERSION_APP_VERSION_SEGMENT_INVALID`
- negative semantic version segments now surface through `VERSION_APP_VERSION_NEGATIVE_INVALID`
- `VERSION_INVALID_APP_VERSION` remains the generic fallback for other invalid version failures

Version API rollout-safe semantics:

- semantically valid but unregistered client versions are still evaluated and return `200`
- `currentVersion` prefers the latest `storeAvailable=true` row
- if no row is marked `storeAvailable=true`, the API falls back to the latest registered row and logs a warning
- use `storeAvailable=false` for app-review, internal-distribution, or not-yet-downloadable rows

Version API customization note:

- the current development line layers internal version evaluation behind application ports/use-cases
- application-layer errors are translated back to the documented HTTP contract at the web adapter boundary
- the supported host override point is the exported `CheckAppVersionUseCase` bean
- `application`, `domain`, and `adapter` package boundaries are now the intended topology; host apps should still avoid depending on internal support/composition types outside those layers
- documented `400` / `404` wire semantics remain unchanged for the controller API

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
- `thumbnailEnabled` (optional query; default `atomic.app.image.thumbnail-enabled`)
- uploader identity parameter (optional by default):
  - enabled when `atomic.app.image.uploader-parameter-enabled=true`
  - parameter name comes from `atomic.app.image.uploader-parameter-name`
  - value is stored into persisted image metadata (`image.uploader_id`)

POST response:

- persisted image metadata response (`adapter.in.web.ImageResponse`) with the same JSON fields as before (id, bucket, file names, urls, dimensions, sizes, status)
- nullable fields are normal:
  - `uploaderId`: null when uploader tracking is disabled or omitted
  - `thumbnailFileName`, `thumbnailUrl`, `thumbnailWidth`, `thumbnailHeight`, `thumbnailFileSize`: null when thumbnail generation is disabled or unavailable
- built-in storage safety budgets still apply under the hood:
  - overly long original object keys or final public URLs fail before the original storage write begins
  - overly long thumbnail keys or thumbnail URLs are downgraded to `thumbnailUploadFailed=true` instead of failing the original upload
  - these budgets do not replace multipart/body-size limits; keep upload-size and temp-disk policy in the host app

DELETE behavior:

- validates `imageId` UUID format
- validates that row matches `{service}` and `{storageService}`
- when uploader tracking is enabled, validates request uploader parameter equals stored metadata `uploaderId`
- resolves delete target from persisted metadata `storageType` only
- rejects delete with `400` when persisted storage mapping is unavailable
- reserves metadata as `DELETE_PENDING` before storage deletion
- deletes original/thumbnail objects from the persisted storage client mapping
- purges metadata row only after storage delete succeeds
- keeps metadata in `DELETE_PENDING` when storage delete fails so a later delete can retry cleanup safely
- host apps can inspect lingering `DELETE_PENDING` rows with `InspectDeletePendingImagesUseCase.inspectDeletePendingImages()`
- host apps can recover lingering `DELETE_PENDING` rows with `RecoverDeletePendingImagesUseCase.recoverDeletePendingImages(limit)` from their own admin job or scheduler; this library does not ship a built-in reaper
- recovery batches claim eligible `DELETE_PENDING` rows before cleanup so overlapping admin/scheduler triggers do not keep retrying the same row in the same batch window
- stale claims are reclaimable after the built-in 15-minute recovery claim timeout

Recovery operator entrypoints:

- `inspectDeletePendingImages()`
  - returns `pendingCount`
  - returns `oldestPendingCreatedAt`
- `recoverDeletePendingImages(limit)`
  - returns `scannedCount`, `recoveredCount`, `failedCount`
  - also returns `remainingPendingCount` and `oldestPendingCreatedAt` after the batch
  - claims eligible pending rows before storage cleanup and releases the claim again when cleanup fails
  - can reclaim stale claims after the built-in 15-minute recovery claim timeout

Example host-owned scheduler:

```kotlin
@Component
class ImageDeletePendingRecoveryJob(
    private val inspectDeletePendingImagesUseCase: InspectDeletePendingImagesUseCase,
    private val recoverDeletePendingImagesUseCase: RecoverDeletePendingImagesUseCase,
) {
  private val logger = LoggerFactory.getLogger(this::class.java)

  @Scheduled(fixedDelayString = "\${jobs.image-delete-recovery.delay-ms:300000}")
  fun run() {
    val snapshot = inspectDeletePendingImagesUseCase.inspectDeletePendingImages()
    if (snapshot.pendingCount == 0L) {
      return
    }

    val result = recoverDeletePendingImagesUseCase.recoverDeletePendingImages(limit = 100)
    logger.info(
        "image delete recovery job completed: scanned={}, recovered={}, failed={}, remaining={}, oldestPendingCreatedAt={}",
        result.scannedCount,
        result.recoveredCount,
        result.failedCount,
        result.remainingPendingCount,
        result.oldestPendingCreatedAt,
    )
  }
}
```

Storage client resolution:

- upload tries keys in order: `service:storageService`, `service::storageService`, `storageService`
- upload resolution also tries exact/upper/lower variants
- delete does not re-resolve from path parameters; it uses the persisted `storageType` value only
- if persisted `storageType` no longer matches a configured storage client key, delete returns `400` without deleting storage objects or metadata
- advanced `atomic-storage` image strategy seams now live under `com.infosung.atomic.storage.image.spi`
  - host apps overriding the old root `com.infosung.atomic.storage.image.*` strategy types must migrate imports

Image API exception semantics:

- stable wire catalog:

| Code | Status | Default message |
|---|---:|---|
| `STORAGE_INVALID_IMAGE_REQUEST` | `400` | `Storage image request is invalid.` |
| `STORAGE_IMAGE_QUALITY_INVALID` | `400` | `Storage image quality is invalid.` |
| `STORAGE_FILE_NAME_REQUIRED` | `400` | `Storage image file name is required.` |
| `STORAGE_IMAGE_ID_INVALID` | `400` | `Storage imageId is invalid.` |
| `STORAGE_IMAGE_PATH_MISMATCH` | `400` | `Storage image does not match request path.` |
| `STORAGE_STORAGE_TYPE_UNAVAILABLE` | `400` | `Stored storage type is unavailable.` |
| `STORAGE_IMAGE_NOT_FOUND` | `404` | `Storage image was not found.` |
| `STORAGE_IMAGE_OWNERSHIP_MISMATCH` | `403` | `Storage image ownership does not match.` |
| `STORAGE_UPLOADER_PARAMETER_REQUIRED` | `400` | `Uploader parameter is required.` |
| `STORAGE_CONFIGURATION_INVALID` | `500` | `Storage configuration is invalid.` |

- invalid quality now surfaces through `STORAGE_IMAGE_QUALITY_INVALID`
- missing original filename now surfaces through `STORAGE_FILE_NAME_REQUIRED`
- invalid delete `imageId` format now surfaces through `STORAGE_IMAGE_ID_INVALID`
- delete request path mismatch now surfaces through `STORAGE_IMAGE_PATH_MISMATCH`
- unavailable persisted delete storage mapping now surfaces through `STORAGE_STORAGE_TYPE_UNAVAILABLE`
- unknown upload storage key and other remaining request-shape failures still surface through `STORAGE_INVALID_IMAGE_REQUEST`
- uploader parameter missing when uploader tracking is enabled surfaces through `STORAGE_UPLOADER_PARAMETER_REQUIRED`
- blank uploader tracking configuration surfaces through `STORAGE_CONFIGURATION_INVALID`
- storage `5xx` codes keep stable `code`/`status`, but the built-in shared handler still masks the default wire message to `Internal Server Error` unless a host replaces that behavior
- original image object key / public URL budget violation before storage write still surfaces through `STORAGE_INVALID_IMAGE_REQUEST`
- other upload/delete exceptions can propagate from underlying storage client or image processing layer
- thumbnail key / thumbnail URL budget violations remain non-fatal original uploads and surface through nullable thumbnail fields plus `thumbnailUploadFailed=true`

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
- redirect endpoint accepts optional `codeChallengeMethod` for PKCE-based native/browser flows.
- redirect endpoint rejects client-supplied `codeVerifier`, `codeChallenge`, and provider-style PKCE parameters.
- when PKCE is used on the redirect API, Atomic generates the verifier server-side, derives the provider-facing `code_challenge`, and keeps the raw verifier only in a short-lived HttpOnly cookie keyed by callback `state`.
- the PKCE verifier cookie reuses the callback-binding cookie policy (`cookieSameSite`, `cookiePath`, `cookieSecure`, `cookieMaxAgeSeconds`), so local HTTP testing with PKCE still needs HTTPS or `callback-binding.cookie-secure=false`.
- frontend sends `relayCode` to your login API.
- login API consumes relay payload using `ConsumeOauthRelayCodeUseCase.consume(relayCode)`.
- the relay module stops at this handoff; your app still issues its own session/JWT/cookie after relay consumption.
- internal implementation is organized in `application`, `domain`, and `adapter` layers. Supported host seams are the exported build/issue/consume use-case beans, `OauthRelayCodeStore`, and the exported web adapter beans.
- state verification is also translated at the adapter boundary into an application-owned verified-state model before callback use-cases read `redirect_uri`, `nonce`, or callback-binding attributes.
- internal composition/support beans may appear in the Spring context, but host apps should not customize those directly. The build redirect use cases are the one exception: they are exported override seams for hosts that intentionally replace the default redirect/callback orchestration.
- the exported `AppOauthRedirectController` type lives on the web adapter boundary.
- boundary note:
  - `AppOauthRedirectController` is the documented HTTP contract. It rejects client-supplied `codeVerifier` and manages callback-binding / PKCE cookies itself.
  - exported build use-cases are a lower-level override seam. If a host intentionally replaces the controller, it may still pass caller-managed `codeVerifier` / `callbackBindingToken` directly.
- default HTTP envelope rendering now flows through the shared `atomic.spring.web` handler instead of a module-local oauth advice bean.
- browser initiation is the intended model for non-web clients too: mobile and desktop apps should normally start the provider flow in the system browser or a system-browser-based tab, let the server receive the provider callback, and then return to an allowlisted app URI with `relayCode`.
- if your client owns a provider SDK-native flow and keeps PKCE/token exchange outside the redirect relay, use `atomic.spring.oauth2` directly instead of forcing that flow through `atomic-app/oauth-redirect`.
- redirect endpoint input `redirectUri` must be an absolute URI and must not include user-info.
- callback binding validates redirect/callback continuity using one-time state attribute + cookie token.
- callback-binding mode can be selected with `atomic.app.oauth.redirect.callback-binding.mode`:
  - `strict` keeps validation enabled and clears the cookie after successful callback
  - `relaxed` keeps validation enabled and preserves the cookie after successful callback
  - `disabled` turns callback binding off
- when `callback-binding.mode` is omitted, legacy `callback-binding.enabled=true|false` still decides the effective mode.
- relay store default type is `entity`.
- selected store dependencies are validated; unselected store dependencies are ignored.
- global relay settings (for example `relay-code-ttl-seconds`) are validated regardless of store type.
- `relay-code-ttl-seconds` must be greater than zero (validated at startup).

Supported client handoff patterns:

| Client type | Recommended launch surface | Typical `redirectUri` shape | Notes |
|---|---|---|---|
| Web | browser | `https://frontend.example.com/oauth/...` | final browser redirect stays on the web origin |
| Mobile | system browser / Custom Tabs / SFSafariViewController | `myapp://oauth/...` or `myapp:/oauth/...` or verified app/universal link | exact emitted URI shape must be allowlisted |
| Desktop | system browser | `http://127.0.0.1:{port}/oauth/...` or desktop custom scheme | loopback host/port/path prefix must be allowlisted exactly; use a fixed pre-allowlisted port in this line |

- `redirectTargetType` logging is URI-shape based, not OS-intent aware. Non-loopback `https://...` targets, including verified app/universal links, are logged as `WEB` because they remain HTTPS redirects at the library boundary.

Concrete relayCode consume flows:

- Web
  - browser opens `GET /oauth/redirect/google?redirectUri=https://frontend.example.com/oauth/callback`
  - provider returns to `GET /oauth/callback/google?...`
  - server redirects browser to `https://frontend.example.com/oauth/callback?relayCode=...`
  - frontend posts that `relayCode` to your login API
  - login API calls `ConsumeOauthRelayCodeUseCase.consume(relayCode)` and then issues your own session/JWT/cookie
- Mobile
  - app starts provider flow in the system browser or browser-based tab
  - provider returns to the same server callback path
  - server redirects to an allowlisted app URI such as `myapp://oauth/callback?relayCode=...`
  - app extracts `relayCode` from the deep link or app link and sends it to your backend login API
  - backend consumes the relay payload and completes account linking or session issuance
- Desktop
  - app starts a fixed loopback listener or custom-scheme handler before opening the system browser
  - provider returns to the server callback path
  - server redirects to the allowlisted desktop URI such as `http://127.0.0.1:49152/oauth/callback?relayCode=...`
  - desktop app receives `relayCode` locally and sends it to your backend login API
  - backend consumes the relay payload and returns the app-specific login/session result

Minimal backend consume example:

```kotlin
@RestController
class RelayLoginController(
    private val consumeOauthRelayCodeUseCase: ConsumeOauthRelayCodeUseCase,
    private val accountService: AccountService,
    private val sessionService: SessionService,
) {
  @PostMapping("/api/login/oauth/relay")
  fun loginWithRelayCode(
      @RequestBody request: RelayLoginRequest,
  ): SessionResponse {
    val payload = consumeOauthRelayCodeUseCase.consume(request.relayCode)
    val principal = accountService.resolveOrCreateFromOauth(payload)
    return sessionService.issueSession(principal)
  }
}
```

`AccountService` and `SessionService` above are host-app examples, not Atomic-provided types.

Relay payload:

- provider name
- `idToken` / `accessToken` / `refreshToken` (when present)
- optional `resolvedIdentity` snapshot when callback can resolve identity cheaply from `id_token`
- token metadata (`expiresInSeconds`, scopes, raw payload, nonce, state attributes)
- `stateAttributes` exclude Atomic internal keys such as callback-binding and PKCE control metadata
- when you bind a host-app account, prefer `resolvedIdentity.providerSubject` as the provider account key. `resolvedIdentity.userId` remains for compatibility.
- `resolvedIdentity` is a convenience snapshot, not a storage schema contract. Persist the fields you own instead of coupling your database schema to the full object.

Provider callback differences:

- Google/Kakao: `code/state` query callback -> provider `exchangeCode(...)`
- Google/Kakao callback resolves optional relay `resolvedIdentity` from `id_token` when present, but relay success does not depend on that enrichment
- Apple: `form_post` callback -> `id_token` is received directly and stored in relay payload
- Apple callback also resolves optional relay `resolvedIdentity` from `id_token`
- Apple callback accepts optional `user` and stores it in relay raw payload.
- Additional callback parameters are merged into relay raw payload for Apple callback.
- For Google/Kakao callback, additional parameters are forwarded to token exchange request but not guaranteed in relay raw payload.
- Apple `GET {callback-endpoint-path}/apple` is rejected with `400` (use `POST` only).

OAuth redirect exception semantics:

- stable wire catalog:

| Code | Status | Default message |
|---|---:|---|
| `OAUTH_REDIRECT_INVALID_REQUEST` | `400` | `OAuth redirect request is invalid.` |
| `OAUTH_CALLBACK_INVALID_REQUEST` | `400` | `OAuth callback request is invalid.` |
| `OAUTH_PROVIDER_UNSUPPORTED` | `400` | `OAuth provider is not supported.` |
| `OAUTH_REDIRECT_URI_INVALID` | `400` | `OAuth redirect URI is invalid.` |
| `OAUTH_CALLBACK_BINDING_INVALID` | `400` | `OAuth callback binding is invalid.` |
| `OAUTH_STATE_INVALID` | `400` | `OAuth callback state is invalid.` |
| `OAUTH_PROVIDER_REMOTE_FAILURE` | `500` | `Upstream OAuth provider request failed.` |
| `OAUTH_APPLE_CALLBACK_POST_ONLY` | `400` | `Apple callback supports POST form_post only.` |
| `OAUTH_REDIRECT_CONFIGURATION_INVALID` | `500` | `OAuth redirect configuration is invalid.` |
| `OAUTH_RELAY_CODE_REQUIRED` | `400` | `OAuth relay code is required.` |
| `OAUTH_RELAY_CODE_INVALID_REQUEST` | `400` | `OAuth relay code request is invalid.` |

- unsupported provider surfaces through `OAUTH_PROVIDER_UNSUPPORTED`
- invalid or disallowed `redirectUri` surfaces through `OAUTH_REDIRECT_URI_INVALID`
- callback binding missing/mismatch/ambiguous cookie surfaces through `OAUTH_CALLBACK_BINDING_INVALID`
- invalid or expired callback state surfaces through `OAUTH_STATE_INVALID`
- broader redirect/callback parsing failures still use `OAUTH_REDIRECT_INVALID_REQUEST` / `OAUTH_CALLBACK_INVALID_REQUEST`
- relayCode missing surfaces through `OAUTH_RELAY_CODE_REQUIRED`
- relayCode invalid / expired / already consumed surfaces through `OAUTH_RELAY_CODE_INVALID_REQUEST` when you call the consume use case or expose your own consume endpoint
- `5xx` oauth codes still keep stable `code` and `status`, but the built-in shared handler masks the wire `message` to `Internal Server Error` by default.
- callback/state validation errors are exposed as public oauth redirect exceptions and mapped to stable `HttpStatusException.code` values at the web boundary.
- upstream provider I/O errors are exposed as public oauth redirect remote-failure exceptions and mapped to stable `HttpStatusException.code` values at the web boundary.
- errors after `relayCode` consumption belong to your login/session API, not to the redirect/callback relay module.
- app modules rely on shared `atomic.spring.web` exception mapping instead of module-local `*HttpExceptionHandler` beans.
- if your host app wants a different error envelope, keep one global `@RestControllerAdvice` and map from public module exceptions or `HttpStatusException.code`.
- empty `allowed-redirect-uri-prefixes` fails startup (fail-fast).
- malformed `allowed-redirect-uri-prefixes` entry also fails startup (absolute URI only; no user-info/query/fragment).
- enabling oauth redirect without both `OauthServiceProvider` and store-backed `OauthStateManager` fails startup.

Security notes:

- Explicitly configure security rules for callback/redirect paths.
- Most services keep redirect/callback endpoints public and enforce authentication at login API.
- `allowed-redirect-uri-prefixes` must be non-empty when redirect API is enabled.
  - match uses scheme/host/port/path-prefix boundary (not raw string startsWith).
  - each entry must be an absolute URI without query/fragment.
  - invalid entry format fails startup.
  - `https://app.example.com/oauth` allows `https://app.example.com/oauth/callback` but rejects `https://app.example.com.evil.com/...`.
  - mobile/custom-scheme deep links are supported when they are absolute URIs and explicitly allowlisted in the exact emitted shape.
    - `myapp://oauth` matches `myapp://oauth/callback`
    - `myapp:/oauth` matches `myapp:/oauth/callback`
    - `myapp://oauth/...` and `myapp:/oauth/...` are different contracts because host/port/path matching still applies
  - for mobile/desktop, prefer system browser handoff plus deep link/app link return instead of embedded webviews unless you intentionally accept that tradeoff
- Keep callback binding enabled in production; change cookie policy only when your provider callback topology requires it.
- Callback-binding uses hardened cookie constraints when enabled: `cookie-name` must start with `__Host-`, `cookie-secure=true`, and `cookie-path=/`.
- default `strict` mode clears the callback-binding cookie after success, so each flow must complete with the cookie issued during redirect.
- use `relaxed` mode when you intentionally prefer multi-tab/back-navigation tolerance over immediate cookie clearing.
- Local plain HTTP callback testing can fail unless you use HTTPS or disable callback binding in local-only environments.

Relay store notes:

- `store.type=in-memory`: no datasource/cache validation.
- `store.type=cache`: validates cache dependencies only (`CacheManager`, `ObjectMapper`).
  - configured `cache-name` must exist in `CacheManager` at startup.
  - the selected cache backend must expose an atomic remove-and-return path (`ConcurrentMap.remove`, native `getAndRemove`, or `asMap().remove`) to preserve one-time relay consume semantics.
  - unsupported cache backends now fail startup by default, or fall back to the in-memory relay store when `store.fail-fast=false`.
- `store.type=entity` (default): validates db dependencies only (`DataSource`, `PlatformTransactionManager`, `ObjectMapper`).
  - `table-name` allows only letters, numbers, and underscores.
- when `store.fail-fast=false`, selected store errors (missing deps, invalid cache-name/ttl, unavailable cache, unsupported atomic cache backend) do not fail startup and fall back to in-memory store.
- in-memory fallback is process-local per instance and can break one-time relay semantics in multi-instance deployments.
- oauth redirect readiness now checks explicit `OauthStateManager.isReplayProtectionEnabled()` capability instead of reflecting internal fields.
- oauth redirect now prefers typed state claims from `atomic.spring.oauth2` and only keeps public facade/web wrappers for compatibility.

App exception seam direction:

- `app-version`: host-facing application exceptions are public and documented; HTTP status translation happens at the web boundary.
- `oauth-redirect`: host-facing redirect/relay exceptions are public and documented; HTTP status translation happens at the web boundary.
- `storage-api`: storage application exceptions remain public and are the supported host seam.
- entity store expects table columns:
  - `relay_code` (PK, string)
  - `payload_json` (text/json string)
  - `expires_at` (timestamp)
  - `created_at` (timestamp)
- cache/entity stores validate expiration on consume (`pop`) and remove consumed relay data.
- for cache backends, configure backend TTL/eviction policy to avoid stale expired keys accumulating.
- default `store.type=entity` + default table `atomic_oauth_relay_code` uses the JPA-backed relay store.
- custom `atomic.app.oauth.redirect.store.entity.table-name` preserves the public runtime contract by switching to the legacy JDBC-backed relay store.
- for entity store, run periodic cleanup (for example `DELETE FROM atomic_oauth_relay_code WHERE expires_at <= NOW()`) to remove unconsumed expired rows.
- `in-memory.cleanup-interval <= 0` disables periodic expired-entry cleanup.

OAuth redirect deployment presets:

| Preset | State replay protection | Relay store | `store.fail-fast` | Callback binding | Notes |
|---|---|---|---|---|---|
| `local-development` | `in-memory-store.enabled=true` is acceptable | `in-memory` is acceptable | `false` or `true` depending on convenience | `strict` by default, `disabled` only for local HTTP callback testing | expect startup warnings about process-local behavior |
| `single-node-production` | in-memory can be acceptable only when the deployment is intentionally single-node | prefer `entity` or verified `cache` | keep `true` | `strict` by default; `relaxed` only intentionally | do not silently rely on fallback semantics |
| `multi-instance-production` | use shared/custom `OauthStateStore` | prefer `entity` or verified `cache` | keep `true` | `strict` by default; `relaxed` only after UX/security review | do not use process-local relay/state storage |

Startup summary:

- oauth redirect startup now logs one summary line with:
  - configured relay store type
  - relay store fail-fast policy
  - effective callback-binding mode
  - whether replay protection is enabled
  - state store type (`IN_MEMORY`, `CUSTOM_OR_SHARED`, `OPAQUE_REPLAY_PROTECTED`)
- follow-up warnings mean:
  - `process-local per instance`
    - one-time semantics depend on local memory; treat as local-only or intentionally single-node
  - `callback binding mode is disabled`
    - only appropriate for local HTTP-only testing or explicitly trusted environments
  - `callback binding mode is relaxed`
    - intentional UX tradeoff; cookie reuse after success remains possible
- treat the startup summary as deployment signal, not optional noise. It is the fastest way to catch local-only relay/state choices before traffic reaches the callback path.

## DDL Examples

The authoritative starting-point assets now ship in module resources for:

- `postgresql`
- `mysql`
- `mariadb`
- `oracle`
- `h2` for test compatibility only

Paths:

- `atomic-app/version`: `META-INF/atomic/sql/{vendor}/service_version.sql`
- `atomic-app/storage-api`: `META-INF/atomic/sql/{vendor}/image.sql`
- `atomic-app/oauth-redirect`: `META-INF/atomic/sql/{vendor}/atomic_oauth_relay_code.sql`

For `service_version` and `image`, these assets now match explicit JPA table/column mappings in code. Startup preflight no longer depends on PostgreSQL-specific catalog SQL; it uses JDBC metadata instead.

Support scope in this line:

- official SQL assets and automated DB verification: `postgresql`, `mysql`, `mariadb`, `oracle`
- test-compatibility assets and validation: `h2`
- best-effort runtime compatibility: other JDBC/JPA-compatible relational databases with equivalent schema, validated by your own CI before production rollout

JPA direction in this line:

- `atomic-app:version` and `atomic-app:storage-api` keep the JPA-centered path. DB variance is handled at the SQL asset and JDBC metadata boundary.
- `atomic-app:oauth-redirect` uses a JPA-backed entity store by default when the shipped table contract `atomic_oauth_relay_code` is used.
- if you configure a custom `atomic.app.oauth.redirect.store.entity.table-name`, the module preserves that public runtime contract by falling back to the legacy JDBC entity-store path.
- Oracle compatibility is verified with a dedicated focused workflow: `.github/workflows/oracle-compatibility.yml`.
- the same focused Oracle checks are re-run in `.github/workflows/publish-maven-central.yml` before release publication.
- repository automation verifies Oracle on the Oracle Free 23 line. If you operate another Oracle line, keep equivalent host-side CI validation before rollout.
- if `atomic.app.oauth.redirect.store.fail-fast=false`, missing relay-store dependencies can fall back to the in-memory relay store; that fallback is process-local per instance and is not multi-instance safe.

The SQL below is the PostgreSQL reference snippet for the shipped contract, including the recovery-claim columns and supporting indexes used in the current line.

- Identifier-like columns (`service`, `platform`, `bucket`, `service_name`, `storage_service`, `storage_type`) keep a bounded `VARCHAR(255)` contract.
- Columns affected by external lengths (`store_url`, `file_name`, `thumbnail_file_name`, `url`, `thumbnail_url`) are shipped as `TEXT`.

```sql
CREATE TABLE IF NOT EXISTS service_version (
  id BIGSERIAL PRIMARY KEY,
  main_version INTEGER NOT NULL,
  minor_version INTEGER NOT NULL,
  patch_number INTEGER NOT NULL,
  require_update BOOLEAN NOT NULL DEFAULT FALSE,
  store_available BOOLEAN NOT NULL DEFAULT TRUE,
  platform VARCHAR(255) NOT NULL,
  service VARCHAR(255) NOT NULL,
  store_url TEXT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_service_version_service_platform_semver
    UNIQUE (service, platform, main_version, minor_version, patch_number)
);

CREATE INDEX IF NOT EXISTS idx_service_version_service_platform_version
  ON service_version (service, platform, main_version DESC, minor_version DESC, patch_number DESC);

CREATE INDEX IF NOT EXISTS idx_service_version_service_platform_required_update
  ON service_version (
    service,
    platform,
    require_update,
    main_version DESC,
    minor_version DESC,
    patch_number DESC
  );
```

```sql
CREATE TABLE IF NOT EXISTS image (
  id VARCHAR(255) PRIMARY KEY,
  bucket VARCHAR(255) NOT NULL,
  service_name VARCHAR(255) NOT NULL,
  storage_service VARCHAR(255) NOT NULL,
  status VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
  uploader_id VARCHAR(255) NULL,
  storage_type VARCHAR(255) NOT NULL,
  file_name TEXT NULL,
  thumbnail_file_name TEXT NULL,
  url TEXT NOT NULL,
  thumbnail_url TEXT NULL,
  width INTEGER NULL,
  height INTEGER NULL,
  file_size BIGINT NOT NULL,
  thumbnail_width INTEGER NULL,
  thumbnail_height INTEGER NULL,
  thumbnail_file_size BIGINT NULL,
  delete_recovery_claim_token VARCHAR(255) NULL,
  delete_recovery_claimed_at TIMESTAMP WITHOUT TIME ZONE NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_image_service_storage
  ON image (service_name, storage_service);

CREATE INDEX IF NOT EXISTS idx_image_status_created_at
  ON image (status, created_at);

CREATE INDEX IF NOT EXISTS idx_image_status_claim_created_at
  ON image (status, delete_recovery_claim_token, created_at);
```

```sql
CREATE TABLE IF NOT EXISTS atomic_oauth_relay_code (
  relay_code VARCHAR(255) PRIMARY KEY,
  payload_json TEXT NOT NULL,
  expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_atomic_oauth_relay_code_expires_at
  ON atomic_oauth_relay_code (expires_at);
```

### Upgrade Note for Existing Tables

If your database already has the older `VARCHAR(255)` shape for externally sized fields, `CREATE TABLE IF NOT EXISTS`
alone will not widen those columns. Apply an explicit migration before rolling out builds that can persist longer values.
If you maintain a custom `image` schema, also ensure the delete-recovery claim columns/index exist before enabling
`atomic.app.image`.

When `atomic.app.version.enabled=true` or `atomic.app.image.enabled=true`, startup now validates these externally sized
columns and fails fast if the old narrow shape is still present. The shipped baseline is `TEXT`, but custom schemas are
also accepted when they use a sufficiently wide `VARCHAR(>=1024)` equivalent. For `atomic.app.image`, startup also
checks that `delete_recovery_claim_token` and `delete_recovery_claimed_at` exist.

PostgreSQL example:

```sql
ALTER TABLE service_version
  ALTER COLUMN store_url TYPE TEXT;

ALTER TABLE image
  ALTER COLUMN file_name TYPE TEXT,
  ALTER COLUMN thumbnail_file_name TYPE TEXT,
  ALTER COLUMN url TYPE TEXT,
  ALTER COLUMN thumbnail_url TYPE TEXT;

ALTER TABLE image
  ADD COLUMN IF NOT EXISTS delete_recovery_claim_token VARCHAR(255) NULL,
  ADD COLUMN IF NOT EXISTS delete_recovery_claimed_at TIMESTAMP WITHOUT TIME ZONE NULL;

CREATE INDEX IF NOT EXISTS idx_image_status_claim_created_at
  ON image (status, delete_recovery_claim_token, created_at);
```

## Operational Checklist

- Prepare database schema for `service_version` and `image` tables before enabling APIs.
- Prefer the shipped module SQL assets as your initial migration baseline instead of copying stale inline snippets.
- If you already run older `service_version` / `image` tables, either widen the documented columns to `TEXT` or use an equivalent `VARCHAR(>=1024)` width before deploying new builds.
- If you maintain a custom `image` schema, add `delete_recovery_claim_token`, `delete_recovery_claimed_at`, and `idx_image_status_claim_created_at` before enabling the image API.
- If you maintain custom `service_version` / `image` SQL, keep the shipped supporting indexes as well (`idx_service_version_service_platform_required_update`, `idx_image_status_created_at`, `idx_image_status_claim_created_at`) so rollout checks and recovery queries keep the tested access paths.
- If startup now fails with a schema-upgrade preflight error, treat it as a migration problem first, not as an API runtime bug.
- if uploader tracking is enabled, add nullable `uploader_id` column to `image` table (or rely on JPA schema generation in non-production environments).
- use `atomic.app.image.thumbnail-enabled=false` only when you intentionally want original-only uploads by default.
- when image delete fails after reservation, treat remaining `DELETE_PENDING` rows as retryable cleanup work.
- if you want proactive cleanup, call `InspectDeletePendingImagesUseCase.inspectDeletePendingImages()` and `RecoverDeletePendingImagesUseCase.recoverDeletePendingImages(limit)` from your own scheduler or admin command path; a built-in scheduler is intentionally out of scope.
- recovery is still host-owned. The library reduces overlapping retries by claiming rows per batch, but it does not become a distributed job system or built-in reaper.
- recovery claims are reclaimable after a built-in 15-minute timeout. If your scheduler interval is longer, document that expectation in your runbook.
- choose uploader parameter name per service (for example `memberId`, `userKey`, `ownerId`) and configure `atomic.app.image.uploader-parameter-name`.
- for OAuth relay, set `atomic.app.oauth.redirect.allowed-redirect-uri-prefixes` in every environment where `atomic.app.oauth.redirect.enabled=true`.
- if `store.type=entity` (default), create relay table (`atomic_oauth_relay_code` or configured table-name) before rollout.
- if `store.type=entity`, schedule expired-row cleanup for unconsumed relay entries.
- Configure `atomic.storage.backends.*` before enabling image API.
- Keep `storageType` key naming consistent with your `{service}` and `{storageService}` path policy.
- Do not rename or remove storage client keys that existing `image.storage_type` rows depend on unless you also migrate stored metadata.
- Enforce multipart size/time limits at application layer.
