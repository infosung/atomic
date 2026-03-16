# Phase 6: OAuth Relay Cache Atomic Consume

## Why This Phase Exists

`CacheOauthRelayCodeStore.pop(...)` currently reads a relay payload and then evicts it in two
steps. That makes the one-time relay contract weaker than the entity store and opens room for
duplicate consume under concurrency.

This phase narrows the gap by requiring cache-based relay consume to use a single atomic
remove-and-return path, or fail/fallback early when the selected cache backend cannot support it.

## Scope

### In Scope

- tighten `CacheOauthRelayCodeStore.pop(...)` so it does not rely on `get(...)` followed by
  `evict(...)`
- validate cache-store readiness against atomic consume support before exposing the store
- preserve current property keys, relay payload shape, and app redirect/controller usage
- add tests first for cache-store contract and auto-configuration fallback/fail-fast behavior
- update user docs to explain which cache path guarantees one-time consume and what happens when
  support is unavailable

### Out Of Scope

- defining universal distributed guarantees across every Spring Cache backend
- redesigning relay storage abstraction beyond the current `OauthRelayCodeStore`
- changing entity store semantics
- removing `store.fail-fast=false` in this phase

## Design Rules

- cache relay consume must use one atomic remove-and-return path when the backend supports it
- if selected cache backend does not expose an atomic consume path, startup should fail or fallback
  according to `store.fail-fast`
- logs must clearly identify which atomic consume path is selected or why fallback/fail-fast
  happened

## Acceptance Criteria

- a cache store backed by an atomic native cache path consumes a relay code only once
- the implementation no longer depends on `cache.get(...)` + `cache.evict(...)` for normal cache
  consume flow
- unsupported cache backends trigger fail-fast or in-memory fallback at startup according to
  `store.fail-fast`
- docs explain the remaining caveat: atomic consume support depends on the selected cache backend

## TDD Sequence

1. Add failing unit tests for `CacheOauthRelayCodeStore` proving consume uses an atomic native path.
2. Add failing auto-configuration tests for unsupported cache backends with `fail-fast=true/false`.
3. Implement the smallest cache-store/accessor changes to satisfy the tests.
4. Update usage and migration docs for the stronger cache-store contract.
5. Run focused oauth-redirect tests, then the broader affected suite.

## Validation Commands

```bash
./gradlew :atomic-app:oauth-redirect:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
