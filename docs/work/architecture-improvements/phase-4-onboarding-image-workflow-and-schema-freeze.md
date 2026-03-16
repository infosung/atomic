# Phase 4: Onboarding, Image Workflow, And Schema Freeze

## Why This Phase Exists

The remaining high-value work now clusters around one theme:

- users still enter through `atomic.app` too early instead of narrower feature modules
- image delete still lacks a retryable cross-resource workflow
- `service_version` and `image` physical column names are not yet fixed in code
- image upload cannot intentionally skip thumbnail generation

These can be improved together without changing core endpoint paths or artifact names.

## Scope

### In Scope

- rewrite onboarding docs so narrow app submodules are the default recommendation and `atomic.app` is clearly a convenience bundle
- make image delete use a retryable `ACTIVE -> DELETE_PENDING -> purge` workflow
- freeze `service_version` and `image` physical table/column names in code with explicit JPA annotations
- allow image upload callers to disable thumbnail generation while preserving current defaults
- add detailed logs around delete state transitions, storage cleanup attempts, and thumbnail generation decisions
- add or update contract tests first for all of the above

### Out Of Scope

- introducing a background delete reaper or outbox
- changing artifact coordinates or removing `atomic.app`
- redesigning `ImageEntity` exposure to a separate public DTO
- changing oauth relay store semantics in this phase

## Design Rules

- keep existing endpoint paths and current request parameters working
- `atomic.app.image.thumbnail-enabled` defaults to `true`
- upload may also override thumbnail generation per request; omission uses the configured default
- delete never goes back to `ACTIVE` once reserved for delete
- a repeated delete on `DELETE_PENDING` retries storage cleanup and then purges metadata
- schema assets, JPA annotations, and user docs must agree on the same physical names

## Acceptance Criteria

- onboarding docs recommend `app-version`, `storage-api`, and `oauth-redirect` before `atomic.app`
- `ServiceVersionEntity` and `ImageEntity` have explicit table/column mappings that match shipped PostgreSQL SQL assets
- delete reserves metadata as `DELETE_PENDING` before storage deletion
- if storage delete fails, metadata remains `DELETE_PENDING`
- if a later delete retry succeeds, the metadata row is purged
- thumbnail generation can be disabled without breaking upload success
- when thumbnail generation is disabled, thumbnail response fields are null and delete still works
- tests fail before implementation and pass after implementation

## TDD Sequence

1. Add failing contract tests for fixed JPA table/column mappings.
2. Add failing image delete workflow tests for `DELETE_PENDING` reservation and retry.
3. Add failing thumbnail-disabled tests at app API and storage service levels.
4. Implement the smallest code changes to satisfy those tests.
5. Rewrite onboarding docs to reflect the new guidance and completed implementation.
6. Run focused app/storage tests, then the broader affected suite.

## Validation Commands

```bash
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-storage:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
