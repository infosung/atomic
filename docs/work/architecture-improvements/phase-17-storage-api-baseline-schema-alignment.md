# Phase 17: Storage-API Baseline Schema Alignment

## Background

After stabilizing `app-version`, the `storage-api` compatibility job still failed in
`AppImageMigrationAssetContractTest` on Spring Boot `4.0.2`.

The failure shape matched the earlier app-version issue:

- migration asset test booted with `spring.jpa.hibernate.ddl-auto=validate`
- compatibility line failed before test methods ran
- failure happened inside Hibernate schema validation

## Decision

Apply the same split used for `app-version`.

- compatibility jobs:
  - keep migration asset tests focused on official SQL asset behavior
  - avoid validator-sensitive bootstrap coupling
- baseline runtime only:
  - keep one dedicated schema alignment test with `ddl-auto=validate`

## Test Strategy

- `AppImageMigrationAssetContractTest`
  - uses the shipped `image.sql`
  - verifies save/find/delete behavior through the official schema
  - verifies documented index existence
  - does **not** depend on Hibernate validator in compatibility jobs
- `AppImageSchemaAlignmentBaselineTest`
  - runs only on the baseline Spring Boot line
  - boots the official schema with `spring.jpa.hibernate.ddl-auto=validate`
  - protects the SQL asset and JPA mapping alignment on the canonical release target

## Isolation Rules

- use a dedicated random PostgreSQL database per storage-api container test class
- drop the `image` table before re-applying the shipped SQL asset in migration/schema-alignment tests

## Verification

- baseline:
  - `./gradlew spotlessCheck test`
- compatibility:
  - `./gradlew :atomic-app:storage-api:test`
