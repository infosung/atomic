# Phase 10: Callback-Binding Policy Mode

## Why This Phase Exists

`oauth-redirect` currently uses one callback-binding behavior only:

- callback binding is either enabled or disabled
- when enabled, redirect reuses an existing cookie token if present
- a successful callback always clears the callback-binding cookie

That default is conservative, but some host apps prefer smoother multi-tab and back-navigation UX.
This phase keeps the current secure default while introducing an explicit policy mode that host apps
can choose.

## Scope

### In Scope

- add a callback-binding policy mode with the candidate shape `strict | relaxed | disabled`
- keep the default behavior conservative and backward compatible
- preserve existing redirect/callback endpoint paths and response envelopes
- update property validation and environment/property binding tests
- add detailed logs that make the chosen callback-binding policy observable

### Out Of Scope

- changing OAuth state verification semantics
- relaxing cookie attribute validation for enabled callback binding
- changing relay-code behavior or relay store selection

## Design Rules

- default behavior must remain equivalent to the current implementation
- `strict` keeps callback binding enabled and clears the cookie after a successful callback
- `relaxed` keeps callback binding enabled but does not clear the cookie after a successful callback
- `disabled` turns callback binding off and should behave like the current `enabled=false` mode
- the legacy `callback-binding.enabled` property must stay supported during migration
- logs must show the resolved callback-binding mode on redirect and callback flows

## Acceptance Criteria

- host apps can select `strict`, `relaxed`, or `disabled` through configuration
- existing setups with no new property keep current strict behavior
- `relaxed` preserves callback-binding verification but does not emit a clearing cookie on success
- `disabled` continues to skip cookie issuance and callback-binding validation
- contract tests lock the mode-specific cookie behavior and property binding

## TDD Sequence

1. Add failing property-binding tests for the new policy mode and legacy compatibility.
2. Add failing controller/HTTP contract tests for `relaxed` and `disabled`.
3. Implement the new property model, mode resolution, validation, and controller behavior with logs.
4. Update user and migration docs, then run focused and broader regression suites.

## Validation Commands

```bash
./gradlew :atomic-app:oauth-redirect:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
