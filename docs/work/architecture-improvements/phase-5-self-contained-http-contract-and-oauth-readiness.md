# Phase 5: Self-Contained HTTP Contract And OAuth Readiness

## Why This Phase Exists

The latest review converged on two immediate issues:

- app module HTTP status semantics are still not self-contained because `HttpStatusException`
  depends on host-app exception mapping for the documented `400/403/404` responses
- oauth redirect readiness still inspects `OauthStateManager` internals via reflection to
  decide whether one-time replay protection is actually available

Both are correctness issues with direct user impact and should be tightened before larger
follow-up work like relay-store atomicity or delete reapers.

## Scope

### In Scope

- make `atomic-app` controllers return documented HTTP status/error envelopes without requiring a
  host app `BaseExceptionHandler`
- keep success envelopes, endpoint paths, and redirect behavior stable while making error wire
  contracts self-contained
- replace oauth redirect replay-protection reflection with an explicit `OauthStateManager`
  capability/contract
- add contract tests first for boot/runtime behavior and public API shape
- update user-facing docs to reflect the new self-contained HTTP behavior and explicit oauth
  readiness contract

### Out Of Scope

- changing cache relay store atomicity
- adding relay-store distributed guarantees beyond the current explicit readiness checks
- adding image delete background reapers or retry schedulers
- redesigning app controllers away from current endpoints or response envelopes

## Design Rules

- documented `HttpStatusException` statuses for app APIs must reach the wire without requiring a
  separate app-level advice bean
- success response envelopes and redirect endpoints must remain stable
- oauth redirect readiness must use a public, explicit contract instead of private-field
  reflection
- if replay protection is absent, startup must still fail-fast with a clear log message

## Acceptance Criteria

- `version`, `storage-api`, and `oauth-redirect` boot smoke tests pass without test-only
  `HttpStatusException` advice beans
- app module error responses still use the documented `BaseResponse.error(...)` envelope and
  intended HTTP status
- `OauthStateManager` exposes replay-protection availability explicitly
- oauth redirect auto-configuration no longer uses reflection to inspect `OauthStateManager`
- docs no longer describe app-module HTTP status mapping as dependent on host-app exception
  mapping for official wire semantics

## TDD Sequence

1. Add failing boot smoke tests proving app module errors still map to documented `400` responses
   without custom test advice.
2. Add failing public contract tests for explicit oauth replay-protection capability.
3. Implement the smallest code changes to satisfy those tests.
4. Update usage and migration docs to reflect the new guarantees and remaining caveats.
5. Run focused tests, then the broader affected suite.

## Validation Commands

```bash
./gradlew :atomic-app:app-version:test :atomic-app:storage-api:test :atomic-app:oauth-redirect:test :atomic-spring-oauth2:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
