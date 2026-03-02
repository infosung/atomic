# Atomic

Atomic is a Kotlin/Spring library suite for backend services.

> Status: Development in progress (pre-release).
> 현재 개발 중(배포 전)입니다.

## Tested Baseline

- Java `25`
- Kotlin `2.3.10`
- Spring Boot `4.0.3`

## Starter-First Policy

`atomic-starter` is the entrypoint for auto-configuration, but it does **not** pull feature modules transitively.

`atomic.app` is also a separate feature module.
It is **not included** in `atomic-starter`, and adding `atomic-starter` alone does not activate app APIs.

You must add:

1. `atomic-starter`
2. `atomic.contract` (required when your app directly uses `BaseResponse` / `HttpStatusException`)
3. only the feature modules you want (`storage`, `spring.web`, `spring.security`, `spring.oauth2`, `app`)

If a feature module is not on classpath, its auto-configuration is skipped.

Relationship summary:

- `atomic.starter`: common infra auto-config entrypoint
- `atomic.app`: app-level API bundle module (independent from starter)
- You can use `atomic.app` without starter for version API (JPA required).
- Image API in `atomic.app` needs storage beans, so typical setup is `atomic.app` + `atomic.starter` + `atomic.storage`.

## Dependency Setup

### Artifact dependencies (Gradle Kotlin DSL)

```kotlin
dependencies {
  implementation("com.infosung:atomic.starter:0.0.1")
  implementation("com.infosung:atomic.contract:0.0.1")

  // add only modules you use
  implementation("com.infosung:atomic.app:0.0.1")
  implementation("com.infosung:atomic.storage:0.0.1")
  implementation("com.infosung:atomic.spring.web:0.0.1")
  implementation("com.infosung:atomic.spring.security:0.0.1")
  implementation("com.infosung:atomic.spring.oauth2:0.0.1")
}
```

### Multi-module local setup

```kotlin
dependencies {
  implementation(project(":atomic-starter"))
  implementation(project(":atomic-contract"))

  // add only modules you use
  implementation(project(":atomic-app"))
  implementation(project(":atomic-storage"))
  implementation(project(":atomic-spring-web"))
  implementation(project(":atomic-spring-security"))
  implementation(project(":atomic-spring-oauth2"))
}
```

## Feature Activation Matrix

| Feature | Required dependency | Activation properties | App-side required components |
|---|---|---|---|
| Contract utilities (`TimeProvider`, `TraceIdGenerator`) | `atomic.starter` + `atomic.contract` | none | use this when app directly uses `BaseResponse` / `HttpStatusException` |
| Storage (`storageClients`, `storageProfiles`, `ImageService`) | `atomic.starter` + `atomic.storage` | `atomic.storage.enabled=true` (default) and valid `atomic.storage.backends.*` | none |
| Common version check API (`GET /api/v1/version/check`) | `atomic.app` (+ datasource/JPA) | `atomic.app.version.enabled=true` | `service_version` table schema and version policy data |
| Common image upload/delete API (`POST/DELETE /api/v1/storage/image/{service}/{storageService}`) | `atomic.app` + `atomic.starter` + storage backend config | `atomic.app.image.enabled=true`, `atomic.storage.enabled=true` (+ optional uploader tracking config) | `image` table schema |
| Web logging/json helpers | `atomic.starter` + `atomic.spring.web` | `atomic.web.enabled=true` (default), `atomic.web.logging.enabled=true` (default) | `LogSaver` implementation + `ApiLogAspect` subclass (for API logging), `BaseExceptionHandler` subclass (for exception mapping) |
| Security JWT helpers | `atomic.starter` + `atomic.spring.security` | `atomic.security.enabled=true` (default), `atomic.security.jwt.enabled=true` (default), JWT keys | your `SecurityFilterChain` that applies `JwtSecurityConfigurerAdapter` |
| OAuth provider beans/service | `atomic.starter` + `atomic.spring.oauth2` | `atomic.oauth2.enabled=true` (default), `atomic.oauth2.state.enabled=true` (default), `atomic.oauth2.state.signing-secret`, `atomic.oauth2.state.in-memory-store.enabled=false` (default), per-provider `enabled=true` | callback/redirect controller endpoints |

## Minimal application.yml (starter-based)

```yaml
atomic:
  app:
    version:
      enabled: true
      endpoint-path: /api/v1/version/check
      default-store-url: https://www.infosung.com
    image:
      enabled: true
      endpoint-path: /api/v1/storage/image
      default-quality: 1.0
      min-quality: 0.1
      max-quality: 1.0
      uploader-parameter-enabled: false
      uploader-parameter-name: uploaderId

  storage:
    enabled: true
    backends:
      S3:
        enabled: true
        type: s3 # supported: s3, r2, minio
        bucket: ${ATOMIC_STORAGE_BUCKET}
        cdn: ${ATOMIC_STORAGE_CDN}
        region: ${ATOMIC_STORAGE_REGION}
        endpoint: ${ATOMIC_STORAGE_ENDPOINT:}
        path-style-access-enabled: ${ATOMIC_STORAGE_PATH_STYLE:false}
        access-key-id: ${ATOMIC_STORAGE_ACCESS_KEY_ID:}
        secret-access-key: ${ATOMIC_STORAGE_SECRET_ACCESS_KEY:}
        session-token: ${ATOMIC_STORAGE_SESSION_TOKEN:}

  web:
    enabled: true
    logging:
      enabled: true
      queue-size: ${ATOMIC_WEB_LOG_QUEUE_SIZE:10000}
      filter:
        enabled: true
        order: 1
        url-patterns:
          - /*

  security:
    enabled: true
    jwt:
      enabled: true
      access-key: ${ATOMIC_SECURITY_JWT_ACCESS_KEY}
      refresh-key: ${ATOMIC_SECURITY_JWT_REFRESH_KEY}
      service-name: ${ATOMIC_SECURITY_JWT_SERVICE_NAME:MyService}
      access-expired-second: ${ATOMIC_SECURITY_JWT_ACCESS_EXPIRED_SECOND:3600}
      refresh-expired-second: ${ATOMIC_SECURITY_JWT_REFRESH_EXPIRED_SECOND:1209600}

  oauth2:
    enabled: true
    state:
      enabled: true
      signing-secret: ${ATOMIC_OAUTH2_STATE_SIGNING_SECRET}
      issuer: ${ATOMIC_OAUTH2_STATE_ISSUER:atomic-oauth-state}
      ttl-seconds: ${ATOMIC_OAUTH2_STATE_TTL_SECONDS:300}
      in-memory-store:
        enabled: false # default false (recommended for multi-instance)
    providers:
      google:
        enabled: ${ATOMIC_OAUTH2_GOOGLE_ENABLED:false}
        client-id: ${ATOMIC_OAUTH2_GOOGLE_CLIENT_ID:}
        client-secret: ${ATOMIC_OAUTH2_GOOGLE_CLIENT_SECRET:}
        server-redirect-uri: ${ATOMIC_OAUTH2_GOOGLE_SERVER_REDIRECT_URI:}
      kakao:
        enabled: ${ATOMIC_OAUTH2_KAKAO_ENABLED:false}
        client-id: ${ATOMIC_OAUTH2_KAKAO_CLIENT_ID:}
        server-redirect-uri: ${ATOMIC_OAUTH2_KAKAO_SERVER_REDIRECT_URI:}
      apple:
        enabled: ${ATOMIC_OAUTH2_APPLE_ENABLED:false}
        client-id: ${ATOMIC_OAUTH2_APPLE_CLIENT_ID:}
        server-redirect-uri: ${ATOMIC_OAUTH2_APPLE_SERVER_REDIRECT_URI:}
```

## Environment Variable Checklist

At minimum by feature:

- Storage
  - `ATOMIC_STORAGE_BUCKET`, `ATOMIC_STORAGE_CDN`, `ATOMIC_STORAGE_REGION`
  - optional credentials: `ATOMIC_STORAGE_ACCESS_KEY_ID`, `ATOMIC_STORAGE_SECRET_ACCESS_KEY`, `ATOMIC_STORAGE_SESSION_TOKEN`
- App APIs
  - no dedicated env var is mandatory
  - `atomic.app.version.default-store-url` can be environment-backed when needed
- Security
  - `ATOMIC_SECURITY_JWT_ACCESS_KEY`, `ATOMIC_SECURITY_JWT_REFRESH_KEY`
- OAuth2
  - `ATOMIC_OAUTH2_STATE_SIGNING_SECRET`
  - provider specific values (for enabled providers only)

## Component Registration You Still Need

### 1) Web module

Implement/register:

- `LogSaver` and `ApiLogAspect` subclass when API logging is enabled
- `BaseExceptionHandler` subclass when exception-response mapping is needed

### 2) Security module

Register `SecurityFilterChain` and apply auto-configured `JwtSecurityConfigurerAdapter`.

### 3) OAuth2 module

Implement callback endpoints (for example `/oauth/redirect/{provider}`, `/oauth/callback/{provider}`) and integrate with `OauthServiceProvider`.

### 4) App module (`atomic.app`)

`atomic.app` provides ready-to-use APIs:

- version check (`AppVersionController`)
- image upload/delete (`AppStorageController`)

Prerequisites:

- Version API needs JPA datasource and `service_version` table.
- Image API needs JPA datasource + `image` table + storage beans (`ImageService`, `storageClients`).

Image uploader identity option (without security coupling):

- `atomic.app.image.uploader-parameter-enabled=true` enables uploader parameter enforcement.
- `atomic.app.image.uploader-parameter-name` defines which request parameter to use (for example `memberId`).
- upload stores that value in `ImageEntity.uploaderId`.
- delete requires same parameter value and rejects mismatch (`403`).
- when enabled in production, align `image` table with nullable `uploader_id` column.

**Important (Spring Security):**
When you use Spring Security, explicitly include the storage API path in your security authorization rules.
Protect `POST/DELETE /api/v1/storage/image/**` (or your custom `atomic.app.image.endpoint-path/**`) as authenticated/authorized endpoints.

## OAuth Provider Notes (starter)

- Providers are independent; enable only what you use.
- You do **not** need to enable Google/Kakao/Apple all together.
- Multi-client mode is supported per provider:
  - use `clients.{clientKey}.*`
  - set `default-client-key` when multiple clients exist
  - keep audience values unique per client
- For multi-instance production, prefer a shared/distributed `OauthStateStore` instead of default in-memory store.
- `atomic.oauth2.state.in-memory-store.enabled` default is `false` because in-memory state store is process-local and can break callbacks in multi-instance deployments.

## Customization / Override Strategy

Most starter beans use `@ConditionalOnMissingBean`, so you can override by registering your own bean.
For beans guarded by name, use the same bean name.

Typical override points:

- Storage
  - `storageClients` (bean name)
  - `storageProfiles` (bean name)
  - `ImageObjectKeyGenerator`, `ImageInputValidator`, `ImageMetadataReader`, `ImageThumbnailGenerator`
- Web
  - `JsonTransfer`, `ServiceLogger`, `ApiLogFilter`
  - `apiLogFilterRegistration` (bean name)
- Security
  - `ClientChannelResolver`, `SecurityCookiePolicy`, `JwtProvider`, `JwtSecurityConfigurerAdapter`
- OAuth2
  - `atomicOauthRestClient` (bean name)
  - `OauthStateStore`, `OauthStateManager`, `OauthServiceProvider`
  - `googleOauthProvider`, `kakaoOauthProvider`, `appleOauthProvider` (bean names)
- App
  - `appVersionCheckService`, `appVersionController`
  - `appImageApiService`, `appStorageController`

## What Was Ambiguous Before (Review Summary)

Before this README rewrite, the main ambiguity points were:

1. `atomic-starter` alone looks sufficient, but feature modules must be added explicitly.
2. Module-specific mandatory properties were spread across separate docs.
3. App-side required components (`LogSaver`, `ApiLogAspect`, `SecurityFilterChain`, callback controller) were not visible in one place.
4. Environment variable setup examples were missing from root-level onboarding.

This README now consolidates those points into one starter-first onboarding path.

## Detailed Guides

- [Usage Overview](docs/usage/overview.md)
- [atomic.starter Guide](docs/usage/atomic-starter.md)
- [atomic.contract Guide](docs/usage/atomic-contract.md)
- [atomic.app Guide](docs/usage/atomic-app.md)
- [atomic.storage Guide](docs/usage/atomic-storage.md)
- [atomic.spring.web Guide](docs/usage/atomic-spring-web.md)
- [atomic.spring.security Guide](docs/usage/atomic-spring-security.md)
- [atomic.spring.oauth2 Guide](docs/usage/atomic-spring-oauth2.md)
