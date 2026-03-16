# Phase 7: `DELETE_PENDING` Recovery Service

## Why This Phase Exists

Image delete now leaves metadata in `DELETE_PENDING` when storage cleanup fails, which is safer than
pretending delete fully succeeded. The missing piece is an operator-friendly recovery path that does
not require replaying the original HTTP delete request.

This phase adds the smallest recovery tool that fits the current module shape: a bean-level recovery
service that host apps can call from their own admin jobs or schedulers.

## Scope

### In Scope

- add an app-level recovery service that scans `DELETE_PENDING` image rows in bounded batches
- retry storage cleanup using the persisted `storageType`
- purge metadata only after storage cleanup succeeds
- keep going when one recovery item fails, with detailed logs and result counts
- auto-configure the recovery service alongside the existing image API when image prerequisites are present
- document how host apps should use the recovery service from their own admin jobs or schedulers

### Out Of Scope

- shipping a built-in scheduler or background reaper policy
- adding public HTTP/admin endpoints for recovery
- changing image upload/delete endpoint paths or request/response contracts
- changing the existing `DELETE_PENDING` request-time delete workflow

## Design Rules

- recovery must operate only on persisted `DELETE_PENDING` rows
- recovery must use the stored `storageType`; it must not guess from request paths or current route input
- a single failed row must not abort the rest of the recovery batch
- logs must clearly show batch start, per-item success/failure, and batch summary

## Acceptance Criteria

- host apps get a bean they can call to recover pending deletes without replaying HTTP delete requests
- recovery reads oldest `DELETE_PENDING` rows first, bounded by a caller-specified limit
- successful storage cleanup purges metadata in the same recovery run
- failed recovery items remain `DELETE_PENDING` and are reported in the result summary
- docs explain that scheduling policy intentionally belongs to the host app

## TDD Sequence

1. Add failing unit tests for batch recovery success, partial failure continuation, and limit validation.
2. Add failing auto-configuration tests for recovery service registration.
3. Implement the smallest recovery service and tx/repository query support.
4. Update usage docs and backlog to reflect that admin recovery exists while built-in scheduling does not.
5. Run focused `storage-api` tests, then the broader affected suite.

## Validation Commands

```bash
./gradlew :atomic-app:storage-api:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
