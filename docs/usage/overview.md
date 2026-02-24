# Usage Overview

> Status: Development in progress (pre-release).
> 현재 개발 중(배포 전)입니다.

## 1. Select Modules

Only include modules you need.

- `com.infosung:atomic.contract:0.0.1`
- `com.infosung:atomic.spring.web:0.0.1`
- `com.infosung:atomic.spring.security:0.0.1`

```kotlin
dependencies {
  implementation("com.infosung:atomic.contract:0.0.1")
  implementation("com.infosung:atomic.spring.web:0.0.1")
  implementation("com.infosung:atomic.spring.security:0.0.1")
}
```

## 2. Bean Registration Policy

- `atomic.contract`: mostly DTO/util/object classes. Usually no bean registration required.
- `atomic.spring.web`: register `JsonTransfer`, logging beans, and your `BaseExceptionHandler` subclass.
- `atomic.spring.security`: register `JwtProvider`, security handlers, and apply `JwtSecurityConfigurerAdapter`.

## 3. No Auto Configuration Yet

Current version expects manual configuration in each server application.

## 4. Next Docs

- [atomic.contract Guide](atomic-contract.md)
- [atomic.spring.web Guide](atomic-spring-web.md)
- [atomic.spring.security Guide](atomic-spring-security.md)
