# Phase 16: App-Version Baseline Schema Alignment

## Background

Phase 15 made `AppVersionMigrationAssetContractTest` stable across the compatibility matrix by removing version-sensitive dependence on Hibernate schema validation.

That improves CI stability, but it also weakens one useful guarantee:

- the official PostgreSQL SQL asset
- and the JPA entity mapping

should still align on the baseline release line.

## Decision

Keep compatibility jobs stable, but restore strong schema alignment verification on the baseline runtime only.

The validation strategy is now split like this:

- compatibility matrix
  - stable SQL asset contract checks
  - no Hibernate `ddl-auto=validate` dependency
- baseline Spring Boot line
  - dedicated schema alignment test
  - official SQL asset + `ddl-auto=validate`

## Test Strategy

- `AppVersionMigrationAssetContractTest`
  - continues to validate official SQL asset behavior without Hibernate validator coupling
- `AppVersionSchemaAlignmentBaselineTest`
  - runs only when the runtime Spring Boot version matches the baseline line
  - boots against the official SQL asset with `spring.jpa.hibernate.ddl-auto=validate`
  - proves the documented SQL asset and JPA mapping still match on the canonical release target

## Why This Is Better

- compatibility jobs stop failing on patch-level validator differences
- baseline confidence remains strong
- the release target for `v0.0.2` still has an explicit schema alignment gate

## Verification

- baseline:
  - `./gradlew spotlessCheck test`
- compatibility:
  - `./gradlew :atomic-app:app-version:test`
