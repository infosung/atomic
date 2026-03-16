# Phase 9: App-Version Policy Resolution Optimization

## Why This Phase Exists

`app-version` currently reads all policy rows for one `service/platform` pair and then resolves the
current version, user version, and required-update target in application memory. That keeps the
logic simple, but it does not scale as cleanly if this library is adopted by services with larger
policy sets.

This phase keeps the external HTTP contract unchanged while moving version resolution closer to the
database and removing full policy-list reads from the main check path.

## Scope

### In Scope

- replace full policy-list reads in `AppVersionCheckService` with targeted repository lookups
- preserve existing version check behavior and response contract
- add SQL/index support when needed for the new lookup pattern
- add tests first proving the service no longer depends on `findAll...` full reads
- add detailed logs for each lookup stage

### Out Of Scope

- changing version check endpoint path, headers, or response JSON
- introducing new version model fields such as `version_code`
- rewriting the version policy domain or adding cache layers

## Design Rules

- the external `VersionCheckResult` contract must stay unchanged
- the main check flow must not read all rows for one `service/platform`
- exact-version lookup, latest-version lookup, and higher-required-update lookup should be explicit
- logs must make each lookup stage observable in production

## Acceptance Criteria

- `AppVersionCheckService` no longer depends on full-row reads for the main policy resolution path
- existing version behavior stays the same for:
  - no policy -> `404`
  - unregistered client version -> `400`
  - required update detection
  - default store URL fallback
- targeted repository methods and SQL assets support the new access pattern

## TDD Sequence

1. Add failing service tests that require targeted repository lookups and reject full policy-list reads.
2. Add failing SQL asset tests if additional indexes are needed.
3. Implement repository methods and service refactor with detailed logs.
4. Run focused `app-version` tests, then broader affected suites.

## Validation Commands

```bash
./gradlew :atomic-app:app-version:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
