# Atomic

Atomic is a shared Kotlin/Spring library set.

> Status: Development in progress (pre-release).
> 현재 개발 중(배포 전)입니다.

## Modules

- `com.infosung:atomic.contract:0.0.1`
- `com.infosung:atomic.spring.web:0.0.1`
- `com.infosung:atomic.spring.security:0.0.1`

## Dependency Example

```kotlin
dependencies {
  implementation("com.infosung:atomic.contract:0.0.1")
  implementation("com.infosung:atomic.spring.web:0.0.1")
  implementation("com.infosung:atomic.spring.security:0.0.1")
}
```

## Usage Guides

- [Usage Overview](docs/usage/overview.md)
- [atomic.contract Guide](docs/usage/atomic-contract.md)
- [atomic.spring.web Guide](docs/usage/atomic-spring-web.md)
- [atomic.spring.security Guide](docs/usage/atomic-spring-security.md)

## Important Notes

- Automatic Spring Boot configuration is not provided yet.
- For `atomic.spring.web` and `atomic.spring.security`, register required beans in your app.
- Logging uses `INFO/DEBUG/TRACE/WARN/ERROR` levels for operational diagnostics.
