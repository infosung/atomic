# Phase 12: Service-Version Uniqueness Guard

## Why This Phase Exists

`atomic.app.version` now has rollout-safe semantics, but the underlying `service_version` schema still
allows duplicate semantic-version rows for the same `(service, platform)`.

That is unsafe for a release centered on the version API because the main read path assumes one logical
row per semantic version when it resolves:

- the latest registered version
- the latest `store_available=true` version
- the exact client version row

If duplicate rows exist for the same semantic version tuple, API behavior becomes dependent on row
ordering and insertion history instead of a stable policy contract.

## Goal

Freeze `service_version` so one `(service, platform, main_version, minor_version, patch_number)` tuple
can exist only once.

## Non-Goals

- No change to HTTP paths, headers, or JSON shape for `atomic.app.version`
- No change to the rollout-safe `store_available` semantics added in Phase 11
- No broader redesign of version policy authoring or admin workflows

## Contract To Add

1. The official PostgreSQL asset must declare a named unique constraint for:
   - `service`
   - `platform`
   - `main_version`
   - `minor_version`
   - `patch_number`
2. The JPA entity mapping must reflect the same uniqueness contract.
3. Applying the official SQL asset must reject duplicate semantic-version rows for the same
   `(service, platform)`.

## TDD Plan

1. Add a failing SQL asset resource contract test for the uniqueness guard text.
2. Add a failing migration-asset integration test that attempts to insert duplicate semantic-version
   rows and expects the database to reject them.
3. Add a failing public-contract test for the JPA `@Table(uniqueConstraints = ...)` mapping.
4. Keep the SQL asset aligned with the JPA `@Table(uniqueConstraints = ...)` contract instead of
   expressing the rule only as a standalone unique index.
5. Update schema and entity mapping until the tests pass.

## Release Impact

This is a schema hardening change for `v0.0.2`.

- Existing clean datasets continue to work unchanged.
- Existing duplicate rows must be cleaned up before applying the uniqueness guard in environments that
  already hold version policy data.
- Migration and usage docs must call out the cleanup requirement explicitly.
