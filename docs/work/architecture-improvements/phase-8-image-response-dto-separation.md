# Phase 8: Image Response DTO Separation

## Why This Phase Exists

`AppStorageController` currently returns `BaseResponse<ImageEntity>`, which means the JPA entity is
also the external response contract. That makes persistence schema changes harder because JPA
fields, annotations, and operational state choices are coupled to the HTTP response model.

This phase separates the external response model without changing the documented upload/delete JSON
shape.

## Scope

### In Scope

- add a dedicated image response DTO for the image upload API
- map persisted `ImageEntity` to the new response DTO inside the controller layer
- preserve existing HTTP response JSON field names and envelope
- keep `AppImageApiService` and persistence flow returning/storing `ImageEntity`
- add tests first for controller response typing and stable wire JSON

### Out Of Scope

- changing image upload/delete endpoint paths or parameters
- changing image API JSON field names
- changing persistence entity shape or table/column mappings
- changing delete workflow or `DELETE_PENDING` behavior
- introducing separate request DTOs in this phase

## Design Rules

- persistence entity stays internal to storage-api persistence/service flow
- controller response uses explicit DTO mapping at the boundary
- logs should clearly show when a persisted image row is being mapped to the external response model
- response JSON must stay wire-compatible with the current documented contract

## Acceptance Criteria

- `AppStorageController` no longer exposes `ImageEntity` as its upload response type
- upload HTTP response JSON remains identical to the previous contract
- persistence tests and migration asset tests continue to rely on `ImageEntity`
- docs describe the external response as image metadata response, not JPA entity

## TDD Sequence

1. Add failing contract tests for the new response DTO and controller response typing.
2. Add failing unit tests proving controller maps persisted entity to the external DTO.
3. Implement the DTO and controller mapping with boundary logs.
4. Update usage/backlog/migration docs to reflect the separation.
5. Run focused `storage-api` tests, then the broader affected suite.

## Validation Commands

```bash
./gradlew :atomic-app:storage-api:test
./gradlew :atomic-app:storage-api:test :atomic-app:app-version:test :atomic-app:oauth-redirect:test :atomic-storage:test :atomic-spring-oauth2:test
```
