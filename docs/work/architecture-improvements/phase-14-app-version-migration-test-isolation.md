# Phase 14: App-Version Migration Test Isolation

## Why This Phase Exists

`AppVersionMigrationAssetContractTest` validates the official `service_version.sql` asset with
`spring.jpa.hibernate.ddl-auto=validate`.

That test must always boot against a fresh schema generated from the official SQL asset, regardless of:

- Spring test-context caching
- Testcontainers reuse behavior
- test-class execution order
- previous schema state in the same PostgreSQL database

## Goal

Make the migration-asset contract test deterministic by isolating its database state from every other
test path.

## Non-Goals

- No change to runtime version API behavior
- No change to the published SQL asset semantics

## Hardening Strategy

1. Give the migration-asset test a dedicated PostgreSQL database name.
2. Drop `service_version` explicitly before applying the official SQL asset in the test bootstrap.
3. Keep the runtime SQL asset unchanged and validate it through the same migration contract tests.

## Validation

- The migration-asset test must continue to prove:
  - official schema boots with `ddl-auto=validate`
  - documented indexes and unique constraint exist
  - duplicate semantic-version rows are rejected
