# atomic.spring.security Guide

## Why Use This Module

Use `atomic.spring.security` when your server issues and validates its own JWT pair (access + refresh) and wants Spring Security integration with minimal wiring.

It provides:

- `JwtProvider`: token issue/verify/expired-claim access
- `JwtSecurityConfigurerAdapter`: filter registration helper
- channel-aware token resolution (`WEB`, `APP`, `UNKNOWN`)
- refresh-cookie based access token re-issue flow

## Quick Start (10 Minutes)

1. Register `JwtProvider`.
2. Add `SecurityFilterChain` with `JwtSecurityConfigurerAdapter`.
3. Add one excluded health endpoint.
4. Verify one protected endpoint with valid/invalid token.

Property reference:
- Full property index (default / required condition / description): [Property Reference by Module](environment-variables.md) -> `atomic.security`

## What You Need to Configure

Required:

- `JwtProvider`
- `SecurityFilterChain` with `JwtSecurityConfigurerAdapter`
- `ObjectMapper` bean for auto-configured `JwtSecurityConfigurerAdapter` path (usually provided by Spring Boot web stack)

Recommended:

- `JwtAuthenticationEntryPoint`
- `JwtAccessDeniedHandler`

Optional:

- `TimeProvider` for deterministic tests or custom clock behavior
- `ClientChannelResolver` for explicit web/app channel distinction (default resolver returns `UNKNOWN`)
- custom `SecurityCookiePolicy`

## Feature-to-Bean Matrix

| Goal | Required | Optional |
|---|---|---|
| Basic JWT auth | `JwtProvider`, `JwtSecurityConfigurerAdapter` | custom handlers |
| Web/App channel-aware token policy | `ClientChannelResolver` | custom domain rules |
| Refresh-cookie access token re-issue | included via filter path | custom cookie policy |
| Deterministic expiration tests | `TimeProvider` injection | timezone customization |

## Quick Configuration

```kotlin
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.security.channel.ClientChannelResolver
import com.infosung.atomic.spring.security.channel.HostBasedClientChannelResolver
import com.infosung.atomic.spring.security.config.JwtSecurityConfigurerAdapter
import com.infosung.atomic.spring.security.handler.JwtAccessDeniedHandler
import com.infosung.atomic.spring.security.handler.JwtAuthenticationEntryPoint
import com.infosung.atomic.spring.security.jwt.JwtProvider
import com.infosung.atomic.spring.security.util.SecurityCookiePolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper

@Configuration
class AtomicSecurityConfig {
  @Bean
  fun timeProvider() = TimeProvider()

  @Bean
  fun jwtProvider(timeProvider: TimeProvider): JwtProvider =
      JwtProvider(
          accessKey = "CHANGE_ME_WITH_STRONG_RANDOM_ACCESS_KEY_64B",
          refreshKey = "CHANGE_ME_WITH_STRONG_RANDOM_REFRESH_KEY_64B",
          accessExpiredSecond = 60 * 15,
          refreshExpiredSecond = 60L * 60L * 24L * 14L,
          serviceName = "MyService",
          timeProvider = timeProvider,
      )

  @Bean
  fun clientChannelResolver(): ClientChannelResolver =
      HostBasedClientChannelResolver(
          webDomains = listOf("www.example.com"),
          apiDomains = listOf("api.example.com"),
      )

  @Bean
  fun securityFilterChain(
      http: HttpSecurity,
      jwtProvider: JwtProvider,
      objectMapper: ObjectMapper,
      timeProvider: TimeProvider,
      clientChannelResolver: ClientChannelResolver,
  ): SecurityFilterChain {
    http.csrf { csrf ->
      // Avoid global CSRF disable. Scope ignore rules to required callback paths only.
      csrf.ignoringRequestMatchers("/oauth/callback/apple")
    }
    http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    http.exceptionHandling {
      it.authenticationEntryPoint(JwtAuthenticationEntryPoint(objectMapper))
      it.accessDeniedHandler(JwtAccessDeniedHandler(objectMapper))
    }

    http.with(
        JwtSecurityConfigurerAdapter(
            jwtProvider = jwtProvider,
            objectMapper = objectMapper,
            excludeUrls = listOf("GET /health", "GET /actuator/health"),
            clientChannelResolver = clientChannelResolver,
            cookiePolicy = SecurityCookiePolicy(sameSite = "Lax", secure = true),
            timeProvider = timeProvider,
        ),
    ) {}

    return http.build()
  }
}
```

If you do not use form callbacks (for example Apple `POST` callback), keep CSRF defaults and avoid broad ignore rules.

## Protect Storage API Path

> **Important**
> If you use `atomic.app` image API, include storage API path in your Spring Security authorization rules.
> Protect `POST/DELETE /api/v1/storage/image/**` (or your custom `atomic.app.image.endpoint-path/**`) as authenticated/authorized endpoints.

Example:

```kotlin
http.authorizeHttpRequests {
  it.requestMatchers("/health", "/actuator/health").permitAll()
  it.requestMatchers("/api/v1/storage/image/**").authenticated()
  it.anyRequest().authenticated()
}
```

## Token Resolution Policy

- `WEB`: `accessToken` cookie first, then refresh-cookie re-issue path
- `APP`: `Authorization: Bearer` only
- `UNKNOWN`: fallback (`Authorization` -> access cookie -> refresh cookie)

This behavior comes from `ChannelAwareTokenResolver`.

## JwtProvider Usage

```kotlin
val jwt = jwtProvider.createJwtDto(id = "123", subject = "USER")
val accessClaims = jwtProvider.getAccessClaims(jwt.accessToken)
val refreshClaims = jwtProvider.getRefreshClaims(jwt.refreshToken)
```

Useful notes:

- `serviceName` blank -> default `InfosungAtomic`
- `getExpiredClaims(...)` is for already-expired access token handling use cases
- use long random keys for `accessKey` and `refreshKey`

## Operational Checklist

- Set secure, long secrets via environment variables.
- Verify `excludeUrls` format is exact: `METHOD /path`.
- Confirm channel resolver domain list matches runtime hosts.
- Validate cookie policy (`secure`, `sameSite`) for deployment environment.
- Add boundary tests around token expiration time.

## Troubleshooting

- Always unauthorized: check token source by channel (`WEB` cookie vs `APP` header).
- Refresh re-issue not working: check `refreshToken` cookie and cookie policy.
- Random expiration test failures: inject fixed `TimeProvider` in tests.
- Public endpoint still protected: check `excludeUrls` exact method/path string.
