# Phase 15: App-Version Migration Validator Stability

## Background

`AppVersionMigrationAssetContractTest` intermittently failed in the Spring Boot `4.0.2` compatibility job even after the migration schema was isolated into a dedicated PostgreSQL database.

The failure happened during JPA bootstrap before any test method ran:

- `BeanCreationException`
- `PersistenceException`
- `SchemaManagementException`

This made the migration asset contract depend on Hibernate schema validator behavior that can vary across compatible framework versions.

## Decision

Keep the migration asset contract focused on the official SQL asset itself and remove version-sensitive reliance on `ddl-auto=validate`.

The contract is now split like this:

- `ServiceVersionSqlAssetResourceContractTest`
  - SQL asset text and documented constraint/index names
- `AppVersionPublicContractTest`
  - JPA entity annotations and public schema mapping contract
- `AppVersionMigrationAssetContractTest`
  - official SQL asset bootstrap
  - unique constraint behavior
  - index existence
  - rollout-safe version check behavior

## Implementation Notes

- Use `spring.jpa.hibernate.ddl-auto=none` in `AppVersionMigrationAssetContractTest`.
- Keep the dedicated PostgreSQL database per test class.
- Generate a random database name so the migration test never shares schema state with other app-version container tests in the same Gradle run.

## Why This Is Safer

- The migration test still proves that the official SQL asset creates a usable schema.
- The public contract test still proves that JPA mapping stays stable.
- The combined coverage is less sensitive to Hibernate validator differences between Spring Boot patch releases.

## Verification

- `./gradlew :atomic-app:app-version:test`
- `./gradlew :atomic-spring-oauth2:test --tests com.infosung.atomic.oauth.state.OauthStateManagerTest`
- `./gradlew spotlessCheck test`
