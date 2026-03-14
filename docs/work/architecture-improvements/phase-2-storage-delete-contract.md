# Phase 2: Storage Delete Contract Tightening

## Why This Phase Exists

Phase 1 fixed oauth redirect correctness without widening public API.

The next highest-value storage change is smaller than a full delete-workflow redesign:

- `storage-api` delete still guesses a storage backend from request path values when the persisted `ImageEntity.storageType` no longer resolves
- that fallback can delete objects from a backend that does not match the original upload backend
- the delete path already logs the fallback, but the behavior is still destructive and configuration-sensitive

## Scope

This phase focuses on `atomic-app/storage-api` delete semantics only.

### In Scope

- stop path-based storage fallback during delete
- require the persisted `ImageEntity.storageType` to resolve directly from configured `storageClients`
- reject delete with `400` when the stored storage mapping is unavailable
- leave storage objects and metadata untouched when storage mapping cannot be resolved from the persisted entity
- add detailed logs around stored storage resolution and delete rejection
- add or update contract tests first for the above behavior

### Out Of Scope

- introducing cross-resource transactional delete semantics
- adding soft-delete, outbox, or reaper workflows
- redesigning `ImageEntity` exposure in HTTP responses
- changing upload behavior or public HTTP endpoint paths

## Acceptance Criteria

- delete uses the persisted `storageType` only
- if the persisted storage mapping is unavailable, delete returns `400`
- when delete is rejected for missing stored mapping, no storage delete call runs
- when delete is rejected for missing stored mapping, metadata delete does not run
- existing HTTP path, request parameters, and response envelope remain unchanged
- logs explain which stored storage mapping was requested and why delete was rejected

## TDD Sequence

1. Replace the current fallback-success test with a failing contract test that expects `400`.
2. Add a companion test that confirms delete still succeeds when the persisted storage mapping exists.
3. Run the focused `storage-api` test suite and confirm failure.
4. Implement the smallest delete-path change needed to pass the new tests.
5. Re-run focused tests, then broader affected-module verification.

## Validation Commands

```bash
./gradlew :atomic-app:storage-api:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
