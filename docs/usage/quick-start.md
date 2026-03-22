# Atomic Quick Start (10-Minute Minimal Setup)

This guide focuses only on getting a working flow quickly.

## 0) Before You Start (Common)

| Item | Minimum |
|---|---|
| App type | Spring Boot Web application |
| Tested baseline | Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.3` |
| Database | Required for `version` and `image` tracks. The `oauth redirect` track needs DB only when you keep the default entity relay store. |
| Module dependencies | Use the track table below as-is. |

Dependency notation:
- Gradle snippets below assume a local multi-module setup (`project(":...")`).
- Published artifact equivalents for `v0.0.5` are:
  - version-only: `implementation("com.infosung:atomic.app.version:0.0.5")`
  - image API: `implementation("com.infosung:atomic.starter:0.0.5")`, `implementation("com.infosung:atomic.app.storage.api:0.0.5")`, `implementation("com.infosung:atomic.storage:0.0.5")`
  - oauth redirect relay API: `implementation("com.infosung:atomic.starter:0.0.5")`, `implementation("com.infosung:atomic.app.oauth.redirect:0.0.5")`, `implementation("com.infosung:atomic.spring.oauth2:0.0.5")`
  - convenience bundle: `implementation("com.infosung:atomic.app:0.0.5")`

Quick decision:
- If you are still pre-production, validate behavior first with this document.
- For production or multi-instance deployment, continue with [advanced-playbook](advanced-playbook.md).
- If you are upgrading an existing host, also review the latest [Release Migration Guide: v0.0.4 -> v0.0.5](../migration/v0.0.4-to-v0.0.5.md).

---

## 1) Three Minimal Adoption Tracks

| Track | When to choose | Minimum dependencies (Gradle) | App prerequisites | Minimum properties |
|---|---|---|---|---|
| `A. version-only` | You only need a version check API quickly | `implementation(project(":atomic-app:app-version"))` | DataSource/JPA + `service_version` table | `atomic.app.version.enabled=true` |
| `B. image API` | You want common image upload/delete API first | `implementation(project(":atomic-starter"))`<br>`implementation(project(":atomic-app:storage-api"))`<br>`implementation(project(":atomic-storage"))` | DataSource/JPA + `image` table | `atomic.app.image.enabled=true` + minimum `atomic.storage.backends.*` |
| `C. oauth redirect relay API` | You want to return `relayCode` instead of exposing OAuth callback tokens directly to frontend | `implementation(project(":atomic-starter"))`<br>`implementation(project(":atomic-app:oauth-redirect"))`<br>`implementation(project(":atomic-spring-oauth2"))` | Login API that consumes `relayCode` + either relay store prerequisites or explicit in-memory/cache choice | `atomic.app.oauth.redirect.enabled=true` + `atomic.app.oauth.redirect.allowed-redirect-uri-prefixes` + `atomic.oauth2.state.signing-secret` + provider minimum values |

If you need more than one app API at once, you can replace the narrow app modules above with the convenience bundle:

```kotlin
implementation(project(":atomic-app"))
```

Published artifact equivalents for the same three tracks:

```kotlin
// A. version-only
dependencies {
  implementation("com.infosung:atomic.app.version:0.0.5")
}
```

```kotlin
// B. image API
dependencies {
  implementation("com.infosung:atomic.starter:0.0.5")
  implementation("com.infosung:atomic.app.storage.api:0.0.5")
  implementation("com.infosung:atomic.storage:0.0.5")
}
```

```kotlin
// C. oauth redirect relay API
dependencies {
  implementation("com.infosung:atomic.starter:0.0.5")
  implementation("com.infosung:atomic.app.oauth.redirect:0.0.5")
  implementation("com.infosung:atomic.spring.oauth2:0.0.5")
}
```

```kotlin
// Convenience bundle
dependencies {
  implementation("com.infosung:atomic.app:0.0.5")
}
```

---

## 2) Minimal YAML by Track

### A. version-only

```yaml
atomic:
  app:
    version:
      enabled: true
```

Notes:
- `service_version.store_available` defaults to `true`.
- if you register review builds or phased-rollout target versions before they are broadly downloadable,
  keep those rows as `store_available=false` so the version API does not advertise them as current
  store targets too early.
- if no matched policy row contributes a non-blank `store_url`, the API falls back to `atomic.app.version.default-store-url` (default `https://www.infosung.com`). Override that default before QA if it is not your actual store destination.
- keep one row per `(service, platform, main_version, minor_version, patch_number)` semantic version;
  duplicate version-policy rows are not a valid schema state in this release line.

### B. image API

```yaml
atomic:
  app:
    image:
      enabled: true
      thumbnail-enabled: true
  storage:
    backends:
      S3:
        type: s3
        region: ap-northeast-2
        bucket: your-bucket
        cdn: https://cdn.example.com
```

Notes:
- `thumbnail-enabled=false` disables thumbnail generation by default for this API.
- callers can still override per request with `thumbnailEnabled=true|false`.

### C. oauth redirect relay API (Google single-client minimal example)

> Warning: secret-like sample values below are for local bootstrap only. Replace before any shared/staging/prod deployment.

```yaml
spring:
  autoconfigure:
    # Use this only for quick-start environments without DB
    exclude: org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration

atomic:
  app:
    oauth:
      redirect:
        enabled: true
        store:
          type: in-memory # quick-start only; production usually prefers entity or shared backend
        allowed-redirect-uri-prefixes:
          - https://web.example.com/oauth
  oauth2:
    state:
      in-memory-store:
        enabled: true # single-node quick-start; for multi-instance use custom/shared store
      signing-secret: CHANGE_ME_WITH_STRONG_RANDOM_STATE_SECRET_AT_LEAST_32_BYTES
    providers:
      google:
        enabled: true
        client-id: your-google-client-id
        client-secret: your-google-client-secret
        server-redirect-uri: https://api.example.com/oauth/callback/google
```

Notes:
- `allowed-redirect-uri-prefixes` is required when `atomic.app.oauth.redirect.enabled=true`.
- `atomic.app.oauth.redirect.enabled=true` also requires both `OauthServiceProvider` and a store-backed `OauthStateManager`. The easiest path is `atomic-starter` plus `atomic-spring-oauth2` with both `atomic.oauth2.state.signing-secret` and `atomic.oauth2.state.in-memory-store.enabled=true`.
- This module ends at relay handoff. Your app must still expose a login/session endpoint that consumes `relayCode` and issues your own cookie/JWT/session.
- For mobile and desktop, prefer launching the provider flow in the system browser or a system-browser-based tab (`Custom Tabs`, `SFSafariViewController`, default desktop browser), then return to the app with an allowlisted deep link/app link. Treat embedded webviews as an exception path you review explicitly with the provider and cookie/state policy.
- Supported client handoff patterns:
  - web: `https://frontend.example.com/...`
  - mobile: deep link/app link such as `myapp://oauth/...` or `myapp:/oauth/...`
  - desktop: loopback URI such as `http://127.0.0.1:49152/oauth/...` or a desktop custom scheme
- `redirectTargetType` in logs is URI-shape based. Verified app/universal links that still use `https://...` remain in the same `WEB` bucket as normal web redirects.
- malformed `allowed-redirect-uri-prefixes` entries fail startup, not first request.
- mobile/custom-scheme deep links are supported, but allowlist matching still uses `scheme + host + port + path-prefix`.
  - `myapp://oauth` allows `myapp://oauth/callback`
  - `myapp:/oauth` allows `myapp:/oauth/callback`
  - keep the configured entry in the exact URI shape your client actually emits; `myapp://oauth/...` and `myapp:/oauth/...` do not match each other
- desktop loopback return is also supported when you explicitly allowlist the exact host/port/path prefix your app listens on
  - this line assumes a fixed pre-allowlisted loopback port; random ephemeral callback ports are not matched
- Default callback-binding uses hardened cookie constraints (`cookie-name` with `__Host-` prefix, `cookie-secure=true`, `cookie-path=/`), so local plain HTTP callbacks can fail with `OAuth callback binding cookie is missing.`
  - For local HTTP-only testing, use HTTPS tunneling or set `atomic.app.oauth.redirect.callback-binding.mode=disabled` (legacy `callback-binding.enabled=false` still works).
- Default callback-binding mode is `strict`, so a successful callback clears the callback-binding cookie and the callback must complete with the cookie minted during redirect.
- If your UX prefers multi-tab/back-navigation tolerance, set `atomic.app.oauth.redirect.callback-binding.mode=relaxed`.
- `spring.autoconfigure.exclude` is a temporary quick-start shortcut for non-DB environments. For production, configure DataSource/store policy explicitly (`entity/cache/custom`).
- If Spring Security is enabled, explicitly configure `permitAll` for redirect/callback endpoints and CSRF policy for Apple `POST` callback path.
- startup now logs an oauth redirect deployment summary (`relayStoreType`, `relayStoreFailFast`, `callbackBindingMode`, `replayProtectionEnabled`, `stateStoreType`).
  - if you see warnings about `process-local per instance`, `callback binding mode is disabled`, or in-memory state replay protection, treat that configuration as local-only or intentionally single-node.
- the startup summary is operational signal, not noise. If it says `process-local per instance`, the current relay/state path is not safe for multi-instance semantics.

RelayCode consume examples:
- Web
  - frontend receives `https://frontend.example.com/oauth/callback?relayCode=...`
  - frontend calls your login API with that `relayCode`
  - backend consumes relay payload and issues your app session/token
- Mobile
  - deep link handler receives `myapp://oauth/callback?relayCode=...`
  - app sends `relayCode` to your login API
  - backend consumes relay payload and completes sign-in/linking
- Desktop
  - loopback or custom-scheme handler receives `relayCode`
  - desktop app sends it to your login API
  - backend consumes relay payload and returns your app login result

Minimal consume endpoint sketch:

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

Preset shortcut:
- `local-development`
  - `atomic.oauth2.state.in-memory-store.enabled=true`
  - `atomic.app.oauth.redirect.store.type=in-memory`
  - optional `atomic.app.oauth.redirect.callback-binding.mode=disabled` only for local HTTP callback testing
- `single-node-production`
  - prefer `atomic.app.oauth.redirect.store.type=entity` or verified `cache`
  - keep `atomic.app.oauth.redirect.store.fail-fast=true`
  - keep `callback-binding.mode=strict` by default
- `multi-instance-production`
  - use shared/custom `OauthStateStore` instead of in-memory replay protection
  - prefer `entity` or verified `cache` relay store
  - keep `store.fail-fast=true`
  - do not rely on process-local fallback

---

## 3) Fail-Fast Checklist (Immediate Verification)

| Track | What to verify immediately |
|---|---|
| `A. version-only` | On `GET /api/v1/version/check`, confirm headers (`X-Service-Name`, `X-Platform`, `X-App-Version`) are present and `service_version` table/rows are ready |
| `B. image API` | Confirm `POST /api/v1/storage/image/{service}/{storageService}` is not `404`, storage backend key (`S3`, etc.) matches request path values, thumbnail behavior matches `atomic.app.image.thumbnail-enabled` / request `thumbnailEnabled`, and your team knows that failed DELETE can leave retryable `DELETE_PENDING` metadata |
| `C. oauth redirect relay API` | Confirm `GET /oauth/redirect/google?redirectUri=...` returns redirect, provider console redirect URI exactly matches `https://{host}/oauth/callback/google`, your login API can consume `relayCode`, and expired/replayed `relayCode` is rejected as expected |

Common failure causes:
- Missing module dependencies while `enabled=true`
- OAuth `signing-secret` length is below 32 bytes
- Callback URI mismatch between provider console and server config
- Image DELETE can reserve metadata as `DELETE_PENDING` before storage cleanup; retry the same DELETE after backend recovery or use the exported `InspectDeletePendingImagesUseCase` / `RecoverDeletePendingImagesUseCase` beans from your own admin or scheduler path. Recovery batches claim eligible rows before cleanup and can reclaim stale claims after 15 minutes, but scheduler ownership still belongs to your app.

---

## 4) Detailed Docs

- [atomic-app](atomic-app.md)
- [atomic-starter](atomic-starter.md)
- [environment-variables](environment-variables.md)
- [advanced-playbook](advanced-playbook.md)
