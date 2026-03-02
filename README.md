# Atomic

Atomic is a shared Kotlin/Spring library set.

> Status: Development in progress (pre-release).
> 현재 개발 중(배포 전)입니다.

## Prerequisites

- Tested with Java `25`
- Tested with Kotlin `2.3.10`
- Tested with Spring Boot `4.0.3`

## Modules

- `com.infosung:atomic.contract:0.0.1`
- `com.infosung:atomic.storage:0.0.1`
- `com.infosung:atomic.spring.oauth2:0.0.1`
- `com.infosung:atomic.spring.web:0.0.1`
- `com.infosung:atomic.spring.security:0.0.1`

## Dependency Example

```kotlin
dependencies {
  implementation("com.infosung:atomic.contract:0.0.1")
  implementation("com.infosung:atomic.storage:0.0.1")
  implementation("com.infosung:atomic.spring.oauth2:0.0.1")
  implementation("com.infosung:atomic.spring.web:0.0.1")
  implementation("com.infosung:atomic.spring.security:0.0.1")
}
```

If your environment cannot resolve pre-release artifacts yet, use module dependency in multi-module builds:

```kotlin
dependencies {
  implementation(project(":atomic-contract"))
  implementation(project(":atomic-storage"))
  implementation(project(":atomic-spring-oauth2"))
  implementation(project(":atomic-spring-web"))
  implementation(project(":atomic-spring-security"))
}
```

## Usage Guides

- [Usage Overview](docs/usage/overview.md)
- [atomic.contract Guide](docs/usage/atomic-contract.md)
- [atomic.storage Guide](docs/usage/atomic-storage.md)
- [atomic.spring.oauth2 Guide](docs/usage/atomic-spring-oauth2.md)
- [atomic.spring.web Guide](docs/usage/atomic-spring-web.md)
- [atomic.spring.security Guide](docs/usage/atomic-spring-security.md)

## Important Notes

- Spring Boot auto-configuration is not provided yet.
- Supported version combinations are managed by CI matrix in `.github/workflows/ci.yml`.
- Compatibility with other patch/minor versions is not guaranteed. Validate with your CI matrix.
- `atomic.spring.oauth2` redirect flow section is browser web-flow guidance; the module itself is not web-only.
- `atomic.spring.web` and `atomic.spring.security` are feature-based: register only the beans needed by the features you enable.
- Logging uses `INFO/DEBUG/TRACE/WARN/ERROR` levels for operational diagnostics.
