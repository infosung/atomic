# Usage Overview

> Status: Development in progress (pre-release).
> 현재 개발 중(배포 전)입니다.

## Before You Start

- Tested baseline: Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.3`
- Additional supported combinations are validated by CI matrix in `.github/workflows/ci.yml`.
- Other patch/minor versions are not guaranteed. Validate in your CI before rollout.
- Atomic does not provide Spring Boot auto-configuration yet.
- You register only the beans/features you use.

## What Atomic Solves

Atomic is a module set for Kotlin/Spring services that want consistent:

- API response shape and shared DTO/util contracts
- storage integration contracts and implementations
- web exception handling and API logging
- JWT authentication filter flow
- social OAuth provider integration

Use only the modules you need.

## 1. Select Modules by Use Case

- `atomic.contract`: shared response/header/exception/util model used by all layers
- `atomic.storage`: storage module (S3-compatible backends such as S3/R2/MinIO, plus media helpers)
- `atomic.spring.web`: API error handling, request/response logging, RestTemplate interceptor/error handler
- `atomic.spring.security`: JWT issue/verify + Spring Security filter integration
- `atomic.spring.oauth2`: OAuth provider integration (Google/Kakao/Apple), redirect flow and id token/userinfo identity resolution

## 2. Dependencies

Maven coordinates:

```kotlin
dependencies {
  implementation("com.infosung:atomic.contract:0.0.1")
  implementation("com.infosung:atomic.storage:0.0.1")
  implementation("com.infosung:atomic.spring.oauth2:0.0.1")
  implementation("com.infosung:atomic.spring.web:0.0.1")
  implementation("com.infosung:atomic.spring.security:0.0.1")
}
```

If pre-release artifact resolution is not available in your environment, use module dependencies:

```kotlin
dependencies {
  implementation(project(":atomic-contract"))
  implementation(project(":atomic-storage"))
  implementation(project(":atomic-spring-oauth2"))
  implementation(project(":atomic-spring-web"))
  implementation(project(":atomic-spring-security"))
}
```

## 3. Quick Start (First Day)

1. Add `atomic.contract` first.
2. Add one feature module only (`storage`, `web`, `security`, or `oauth2`).
3. Register only minimum required beans from that guide.
4. Run one smoke endpoint.
5. Add optional features after baseline works.

## 4. Module Combination Matrix

| Goal | Modules | First Setup |
|---|---|---|
| Standard API response + shared contracts | `contract` | Use `BaseResponse`, `HttpStatusException` |
| Object storage and media processing | `storage` | register `Map<String, StorageClient>` + `Map<String, StorageProfile>` with matching `storageType` keys |
| Exception response standardization | `contract` + `spring.web` | `BaseExceptionHandler` subclass |
| API request/response audit logs | `contract` + `spring.web` | `JsonTransfer` + `LogSaver` + `ServiceLogger` + `ApiLogAspect` + `ApiLogFilter` |
| JWT auth for your API | `contract` + `spring.security` | `JwtProvider` + `SecurityFilterChain` |
| Social login redirect flow | `contract` + `spring.oauth2` | provider beans + `OauthStateManager` + callback controller |
| Full typical server stack | all modules | start with `contract` -> `web` -> `security` -> `oauth2` |

## 5. Configuration Policy

- Spring Boot auto-configuration is not provided yet.
- You explicitly register beans you want to use.
- Most setup is feature-based: start with minimum beans, then add optional beans as needed.

## 6. Operational Checklist (All Modules)

- Keep secrets out of source code and inject via environment.
- Fix timezone/clock policy once and use consistently.
- Define one response format policy for all controllers.
- Verify error mapping behavior in non-200 scenarios.
- Add integration tests for auth and callback flows before release.

## 7. Troubleshooting Index

- Response shape mismatch: check `BaseResponse` usage consistency.
- Storage upload `Unknown storageType`: check storage client/profile map keys and call-site `storageType` value.
- OAuth callback errors: check state and redirect URI mapping.
- JWT unauthorized unexpectedly: check channel resolver and token source.
- API logs missing: check `ServiceLogger.send()` scheduling.
- Artifact not found: switch to `project(":...")` dependency until repository resolution is ready.

## 8. Recommended Reading Order

1. [atomic.contract Guide](atomic-contract.md)
2. [atomic.storage Guide](atomic-storage.md)
3. [atomic.spring.web Guide](atomic-spring-web.md)
4. [atomic.spring.security Guide](atomic-spring-security.md)
5. [atomic.spring.oauth2 Guide](atomic-spring-oauth2.md)
