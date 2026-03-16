# Phase 1: Contract And Correctness

## Why This Phase Exists

The review converged on a small set of high-risk issues that can be improved without first doing a large architectural rewrite:

- OAuth callback state handling is inconsistent across providers
- `atomic.app.oauth.redirect.enabled=true` does not always mean the feature is actually usable
- malformed redirect allowlist entries can survive startup and fail only at request time
- operational visibility is thin around oauth redirect validation and fail-fast decisions

## Scope

This phase focuses on `atomic-app/oauth-redirect`.

### In Scope

- make all OAuth callback paths use one-time state verification semantics
- fail fast when oauth redirect API is enabled but required oauth beans are missing
- require replay-protected `OauthStateManager` configuration for oauth redirect enablement
- validate allowed redirect URI prefixes at startup, not only at request time
- clear callback-binding cookie after successful callback completion
- add detailed logs around redirect validation, callback processing, and startup prerequisite failures
- add or update contract tests first for the above behavior

### Out Of Scope

- changing published module structure or renaming artifacts
- replacing sync image upload/delete flow
- redesigning image API DTO/entity boundaries
- changing version-check query model for performance
- distributed atomic consume support for generic Spring Cache relay stores

## Acceptance Criteria

- repeated callback with the same signed state is rejected for Google/Kakao-style flows
- invalid `allowed-redirect-uri-prefixes` fails context startup
- enabling oauth redirect without `OauthServiceProvider` or `OauthStateManager` fails context startup with explicit guidance
- enabling oauth redirect without a store-backed `OauthStateManager` fails context startup
- successful callback clears the callback-binding cookie when callback binding is enabled
- tests assert the above behavior before implementation is considered done
- logs make it clear why startup failed or a callback was rejected

## TDD Sequence

1. Extend contract tests for callback state consumption and startup prerequisite validation.
2. Run the focused oauth redirect test suite and confirm failure.
3. Implement the smallest code change needed to pass the new tests.
4. Re-run focused tests, then the broader affected module set.

## Validation Commands

```bash
./gradlew :atomic-app:oauth-redirect:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
