# Advanced Operations Playbook (Production)

This playbook is for teams moving from quick-start validation to production-grade operation.

Quick start first:
- [Atomic Quick Start (10-Minute Minimal Setup)](quick-start.md)

## 1) When to Move Beyond Quick Start

Move to advanced operation when any of the following is true:

- You are scaling from single instance to multi-instance (for example HPA, ECS/Fargate scale-out)
- You now have incident/error-budget targets (login failure rate, API 5xx budget, etc.)
- Security requirements are tightening (JWT key rotation, one-time OAuth state, strict cookie policy)
- External retry traffic becomes meaningful (payments/orders/callback flows need idempotency)
- You must integrate heartbeat signals with external monitoring/alerting
- You need strict consistency of exception response format and status mapping (`HttpStatusException`)

## 2) Module-Specific Production Hardening Checklist

### A. Storage (`atomic.storage`)

- [ ] Keep `storageClients` keys and `storageProfiles` keys identical (for example both `"S3"`)
- [ ] Ensure `StorageProfile.bucket` matches the actual `StorageClient` bucket
- [ ] Ensure required values exist: `atomic.storage.backends.<backend>.bucket/cdn/region`
- [ ] For static credentials, always set `access-key-id` and `secret-access-key` as a pair
- [ ] Validate `java.io.tmpdir` permissions/capacity for peak concurrent uploads
- [ ] Define upload size/time limits in app layer (multipart limits)
- [ ] Monitor thumbnail failure signals (`thumbnailUploadFailed=true`)

References:
- [atomic.storage Guide](atomic-storage.md)
- [Property Reference by Module](environment-variables.md) (`atomic.storage`)

### B. App APIs (`atomic.app`)

- [ ] Enable only the APIs you actually use (`version`, `image`, `oauth redirect`)
- [ ] Version API: prepare `service_version` table and policy rows
- [ ] Version API: keep review/pre-rollout rows with `store_available=false` until they are safe force-update targets
- [ ] Version API: deduplicate semantic-version rows before applying the official uniqueness guard
- [ ] Version API: if you override host behavior, keep using the exported `CheckAppVersionUseCase` bean; internal support types outside the layered package boundaries are not a public extension contract
- [ ] Image API: prepare `image` table, `storageClients`/`ImageService`, and endpoint security rules
- [ ] OAuth redirect API: set non-empty `allowed-redirect-uri-prefixes` in every enabled environment, and pin production prefixes to real domains only
- [ ] OAuth redirect + Spring Security: define `permitAll` for redirect/callback and explicit CSRF policy for Apple `POST` callback
- [ ] OAuth redirect API: document which login/session endpoint consumes `relayCode`; the library does not complete your app login on its own
- [ ] Document relay store policy (`entity`/`cache`/`in-memory`) and fail-fast behavior by deployment model
- [ ] Image API: decide who owns `DELETE_PENDING` recovery, how it is invoked, and what alert threshold is acceptable
- [ ] Image API: decide whether synchronous request-thread image processing still fits your latency/error budget

References:
- [atomic.app Guide](atomic-app.md)
- [Property Reference by Module](environment-variables.md) (`atomic.app.version`, `atomic.app.image`, `atomic.app.oauth.redirect`)

### C. OAuth2 (`atomic.spring.oauth2`)

- [ ] Set `atomic.oauth2.state.signing-secret` to at least 32 bytes and inject via secret manager
- [ ] Ensure provider console redirect URI exactly matches `server-redirect-uri`
- [ ] Enforce required `state` verification path (`exchangeCode` must receive state)
- [ ] Treat one-time state policy as mandatory in production (`OauthStateStore` or custom shared store)
- [ ] Default `atomic.oauth2.state.in-memory-store.enabled=false` is valid; still define explicit one-time state strategy
  - Single-node: consider `in-memory-store.enabled=true`
  - Multi-instance: define custom/shared state store strategy
- [ ] Do not pass tokens (`id_token`, `access_token`) via redirect query directly (use relay code flow)
- [ ] Check provider capability (`supports`) before calling refresh/revoke
- [ ] Fix nonce/audience policy per provider

References:
- [atomic.spring.oauth2 Guide](atomic-spring-oauth2.md)
- [Property Reference by Module](environment-variables.md) (`atomic.oauth2`, `atomic.oauth2.providers.*`)

### D. Security (`atomic.spring.security`)

- [ ] Use sufficiently strong random values for `atomic.security.jwt.access-key` and `refresh-key`
- [ ] Keep `exclude-urls` format strict (`METHOD /path`)
- [ ] Validate cookie policy by environment (`same-site`, `secure`, `domain`, `path`)
- [ ] Verify token source policy by channel (WEB cookie vs APP Authorization header)
- [ ] If using `atomic.app.image`, enforce rules on `/api/v1/storage/image/**`
- [ ] Automate boundary tests (just-issued / just-before-expiry / just-after-expiry)

References:
- [atomic.spring.security Guide](atomic-spring-security.md)
- [Property Reference by Module](environment-variables.md) (`atomic.security`)

### E. Idempotency (`atomic.spring.idempotency`)

- [ ] Match `include-methods` to actual write APIs
- [ ] Design `Idempotency-Key` with actor/tenant scope (for example `userId:uuid`)
- [ ] In multi-instance deployments, use a shared store (not in-memory)
- [ ] Set `processing-ttl-seconds` longer than max processing time
- [ ] Agree client behavior for replay with omitted body (`max-cached-body-bytes`)
- [ ] Set `fail-open` based on service criticality (default `true` favors availability)
- [ ] Monitor `409 Processing/FingerprintMismatch` trends

References:
- [atomic.spring.idempotency Guide](atomic-spring-idempotency.md)
- [Property Reference by Module](environment-variables.md) (`atomic.idempotency`)

### F. Heartbeat (`atomic.heartbeat`)

- [ ] Validate `atomic.heartbeat.provider.healthchecks.base-url`
- [ ] Tune with principle `ping.interval >= max(check timeout)`
- [ ] Align `checks.db/redis.required` with failure-propagation policy
- [ ] Explicitly define `checks.missing-bean-policy` with `checks.db/redis.enabled=true` (default `warn` can skip checks)
- [ ] Document reason for selected `dedup.mode` (`none`, `leader`, `per-instance`)
- [ ] For `leader` mode, ensure backend prerequisites are ready (`redis`/`jdbc`/`custom`)
- [ ] For JDBC leader, use migration-managed DDL in production (`auto-create-table=false`)
- [ ] Separate alarms for dedup backend failures vs heartbeat target failures

References:
- [atomic.heartbeat Guide](atomic-heartbeat.md)
- [Property Reference by Module](environment-variables.md) (`atomic.heartbeat`)

## 3) App-Module Production Decision Matrix

| Concern | Single-node / local validation | Multi-instance / production |
|---|---|---|
| OAuth state replay protection | `atomic.oauth2.state.in-memory-store.enabled=true` is acceptable | use a shared/custom `OauthStateStore`; do not rely on process-local state storage |
| OAuth relay store | `in-memory`, `cache`, or `entity` can be acceptable depending on convenience | prefer `entity` or a cache backend with verified atomic consume semantics; keep `store.fail-fast=true` |
| Callback-binding mode | `strict` default, `relaxed` when you want easier multi-tab/back-navigation validation, `disabled` only for local escape-hatch testing | keep `strict` unless product UX explicitly prefers `relaxed`; treat `disabled` as non-production |
| Image delete cleanup | manual retry is usually enough | define an admin job or operator path that calls `RecoverDeletePendingImagesUseCase.recoverDeletePendingImages(limit)`; overlapping invocations now claim rows per batch, but host ownership still applies |
| Exception envelope | built-in app advice is enough for quick validation | verify advice precedence if your platform already standardizes a different error envelope |

## 4) `DELETE_PENDING` Runbook

Treat `DELETE_PENDING` as retryable cleanup work, not as a successful delete.

Detection:
- count rows in `image` where `status='DELETE_PENDING'`
- alert if the count stops returning to zero after storage recovery or if old rows keep aging

Minimum operator action:
1. confirm the backing storage client key named by `image.storage_type` still exists
2. restore the failing storage/backend condition
3. retry cleanup by replaying the original delete or invoking `RecoverDeletePendingImagesUseCase.recoverDeletePendingImages(limit)`
4. verify the row is purged only after storage objects are actually deleted

Concurrency note:
- `recoverDeletePendingImages(limit)` claims eligible rows per batch before storage cleanup, which reduces duplicate work from overlapping operator triggers
- stale claims become reclaimable after the built-in 15-minute recovery claim timeout
- this does not turn the module into a general distributed worker system; host teams still own scheduler cadence, timeout policy, and escalation

Recommended alerts:
- non-zero `DELETE_PENDING` count older than your normal storage incident window
- repeated recovery failures for the same image id
- rising delete failure rate after storage client key or bucket changes

Recommended ownership:
- product team owns the scheduler/admin trigger cadence
- platform/ops team owns the alert threshold and recovery runbook

## 5) OAuth Redirect Deployment Rules

| Deployment model | State replay protection | Relay store | Callback binding | Operational note |
|---|---|---|---|---|
| `local-development` | `atomic.oauth2.state.in-memory-store.enabled=true` is acceptable | `in-memory` is acceptable | `strict` by default, `disabled` only for local HTTP callback testing | startup warnings about `process-local per instance` are expected |
| `single-node-production` | in-memory can be acceptable only when single-node is intentional and documented | prefer `entity` or verified `cache` | `strict` by default; `relaxed` only when the UX tradeoff is intentional | keep `store.fail-fast=true` unless you explicitly accept local fallback behavior |
| `multi-instance-production` | use a shared/custom `OauthStateStore` | prefer `entity` or verified `cache` | `strict` by default; `relaxed` only after review | do not rely on process-local fallback or in-memory replay protection |

Interpret the startup logs literally:
- `OAuth redirect deployment summary`
  - quick snapshot of relay store, fail-fast, callback-binding mode, and replay protection shape
- warning contains `process-local per instance`
  - current relay/state path is not multi-instance safe
- warning contains `callback binding mode is disabled`
  - treat the config as local-only unless you have an explicit exception
- relay/code handoff still requires your own login/session issuance
  - `ConsumeOauthRelayCodeUseCase.consume(relayCode)` gives you relay payload, not a completed app session

## 6) Synchronous Image Processing Envelope

The current image path is intentionally synchronous: request upload, original object upload,
optional thumbnail generation, and thumbnail upload all happen on the request thread.

Treat this path as the supported envelope for:
- interactive user uploads
- a modest number of image variants per request
- workloads where request latency can legitimately include image resize/upload time

Do not treat it as the default answer for:
- bulk ingestion or backfill jobs
- high fan-out media generation
- workloads with strict latency SLOs that cannot absorb image-processing time
- environments already showing temp-disk pressure or CPU contention from image transforms

Plan an async/offload path when any of these start happening:
- upload traffic begins to compete with request latency budgets
- thumbnail generation failures or timeouts become operationally common
- operators need queue-based retries or isolation from request spikes
- your service needs explicit throughput guarantees independent of end-user HTTP requests

## 7) Common Misconfigurations and Diagnosis

### A. Storage

Symptoms:
- `unknown storageType` on upload, or malformed returned URLs

Diagnosis:
- Compare request `storageType` with `storageClients/storageProfiles` keys
- Check missing `cdn` (`atomic.storage.backends.<backend>.cdn`)
- Check bucket/profile mismatch

Action:
- Fix key/bucket/CDN consistency first, then retry

### B. OAuth2

Symptoms:
- Invalid/expired state at callback
- Provider token exchange failure

Diagnosis:
- Check `signing-secret`, `issuer`, `ttl-seconds`
- Verify exact match between provider console redirect URI and server config
- Check duplicate callback or duplicate state consume path

Action:
- Simplify to a single state-consume path and block callback duplication

### C. Security

Symptoms:
- Persistent 401/403 or refresh re-issue failure

Diagnosis:
- Verify token location by channel (`WEB` cookie, `APP` Authorization)
- Validate `exclude-urls` format/typos
- Check cookie `secure/sameSite/domain` against actual domain/HTTPS topology

Action:
- Redefine channel and cookie policy to match deployment topology

### D. Idempotency

Symptoms:
- Duplicate request processed as new (not replayed)
- Rapid increase in `409`

Diagnosis:
- Verify stable `Idempotency-Key` generation on client
- Check if in-memory store is used in multi-instance deployment
- Verify fingerprint policy matches business requirement (body-less default caution)

Action:
- Move to shared store + custom fingerprint resolver

### E. Heartbeat

Symptoms:
- Duplicate heartbeat emissions or no emissions in multi-instance deployment

Diagnosis:
- Check `dedup.mode` against actual deployment model
- Validate backend connectivity/lock renew in `leader` mode
- Confirm stale checks trigger expected fail behavior

Action:
- Re-select dedup policy and retune renew/lease/check interval/timeout

## 8) `HttpStatusException` Mapping Guide (Core)

### Scenario 1: With `BaseExceptionHandler` (Recommended)

Setup:
- Register `@RestControllerAdvice` class extending `BaseExceptionHandler`

Behavior:
- `HttpStatusException(status, message)` status is reflected in HTTP response
- Response is standardized as `BaseResponse.error(e)` for mapped exceptions, and `5xx` response messages are masked to `Internal Server Error`
- `NoResourceFoundException` maps to 404, other uncaught exceptions map to 500

Use when:
- You require consistent response/error format across controllers

### Scenario 2: Without `BaseExceptionHandler`

Behavior:
- `HttpStatusException` goes through default Spring exception path and may appear as 500 depending on app defaults
- Response schema can diverge from `BaseResponse`

Risk:
- Clients can break due to status/format mismatch

Minimum guidance:
- If your service layer throws `HttpStatusException`, register explicit exception mapping

Example:

```kotlin
@RestControllerAdvice
class AppExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment) {
  override fun alert(e: Exception, message: String) {
    // Slack/Webhook integration
  }
}
```

## 9) Release Gate (Before Production)

- [ ] Module checklists completed (storage/oauth2/security/idempotency/heartbeat)
- [ ] Integration tests pass for `HttpStatusException` 400/401/404/500 cases
- [ ] Multi-instance verification complete for idempotency/heartbeat
- [ ] `DELETE_PENDING` recovery ownership and alerts are defined when image API is enabled
- [ ] OAuth redirect store/state policy matches deployment model (`single-node/local` vs `multi-instance/production`)
- [ ] If you rely on official Oracle support, run `.github/workflows/oracle-compatibility.yml` or the equivalent focused Oracle gate before release
- [ ] Synchronous image-processing latency is acceptable for the intended workload envelope
- [ ] Logging policy verified: `ApiLogAspect`/`RestClientErrorHandler` do not emit raw body dumps; keep `ServiceLogger` TRACE off in production
- [ ] No secret/key/token values remain in repository config
- [ ] Observability metrics prepared (401/403/409/429/5xx, state failure rate, heartbeat send failure rate)

## 10) Detailed Links

- [atomic.storage Guide](atomic-storage.md)
- [atomic.spring.oauth2 Guide](atomic-spring-oauth2.md)
- [atomic.spring.security Guide](atomic-spring-security.md)
- [atomic.spring.idempotency Guide](atomic-spring-idempotency.md)
- [atomic.heartbeat Guide](atomic-heartbeat.md)
- [atomic.spring.web Guide](atomic-spring-web.md)
- [Property Reference by Module](environment-variables.md)
