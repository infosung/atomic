# Phase 3: PostgreSQL Migration Assets

## Why This Phase Exists

The review and current tests show that persistence-backed app modules still rely on one of these:

- JPA/Hibernate schema generation in tests
- manual DDL examples in user docs
- raw SQL assumptions embedded in JDBC code

That leaves a gap between documented schema expectations and a versioned artifact that users can apply directly.

## Scope

This phase adds official PostgreSQL schema assets for the persistence-backed app modules.

### In Scope

- ship module-local PostgreSQL SQL assets in:
  - `atomic-app/version`
  - `atomic-app/storage-api`
  - `atomic-app/oauth-redirect`
- use module-owned resource paths instead of shared `db/migration` to avoid cross-module versioning collisions
- add contract tests that apply only the shipped SQL asset and verify module behavior against PostgreSQL
- update user docs to point at the shipped SQL assets as the authoritative starting point

### Out Of Scope

- adding Flyway or Liquibase as a required dependency
- auto-running schema creation at runtime for app modules
- solving cross-database portability in this phase
- redesigning table ownership or persistence models

## Asset Policy

- asset style: plain SQL
- database target: PostgreSQL
- ownership: one SQL asset per publishable module
- resource path:
  - `META-INF/atomic/sql/postgresql/service_version.sql`
  - `META-INF/atomic/sql/postgresql/image.sql`
  - `META-INF/atomic/sql/postgresql/atomic_oauth_relay_code.sql`
- these assets are manual migration starting points, not runtime auto-migration hooks

## Acceptance Criteria

- each persistence-backed app module ships one PostgreSQL schema asset in `src/main/resources`
- `version` tests pass with schema created from shipped SQL asset and `ddl-auto=validate`
- `storage-api` tests pass with schema created from shipped SQL asset and `ddl-auto=validate`
- `oauth-redirect` entity relay store tests pass with schema created only from shipped SQL asset
- user docs reference the shipped assets instead of relying only on inline DDL examples

## TDD Sequence

1. Add failing contract tests that bootstrap PostgreSQL using the new resource paths.
2. Confirm tests fail because the SQL assets do not exist yet.
3. Add the official SQL assets with the minimum indexes and constraints required by current code.
4. Re-run focused tests until they pass.
5. Update user docs and backlog status.

## Validation Commands

```bash
./gradlew :atomic-app:version:test :atomic-app:storage-api:test :atomic-app:oauth-redirect:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
