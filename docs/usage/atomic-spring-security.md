# atomic.spring.security Guide

## Summary

`atomic.spring.security` provides JWT-based authentication filter flow with:

- access/refresh token issue and validation
- channel-aware token resolution (WEB/APP/UNKNOWN)
- cookie re-issue flow from refresh token

## Required Beans (Typical)

1. `JwtProvider`
2. `JwtAuthenticationEntryPoint`
3. `JwtAccessDeniedHandler`
4. `SecurityFilterChain` with `JwtSecurityConfigurerAdapter`

Optional:

- `TimeProvider` (custom clock/timezone)
- `ClientChannelResolver` (for domain-based WEB/APP detection)

## Example Configuration

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
          accessKey = "replace-with-strong-access-key",
          refreshKey = "replace-with-strong-refresh-key",
          accessExpiredSecond = 60 * 15,
          refreshExpiredSecond = 60L * 60L * 24L * 14L,
          serviceName = "MyService",
          timeProvider = timeProvider,
      )

  @Bean
  fun authenticationEntryPoint(objectMapper: ObjectMapper) =
      JwtAuthenticationEntryPoint(objectMapper)

  @Bean
  fun accessDeniedHandler(objectMapper: ObjectMapper) =
      JwtAccessDeniedHandler(objectMapper)

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
    http.csrf { it.disable() }
    http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    http.exceptionHandling {
      it.authenticationEntryPoint(authenticationEntryPoint(objectMapper))
      it.accessDeniedHandler(accessDeniedHandler(objectMapper))
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

## Token Resolution Policy

- `WEB`: cookie first, then refresh-cookie reissue. `Authorization` is ignored.
- `APP`: `Authorization: Bearer` only.
- `UNKNOWN`: backward-compatible fallback (`Authorization` -> cookie -> refresh-cookie).

## JwtProvider Tips

- `serviceName` defaults to `InfosungAtomic` when blank.
- Keep access/refresh keys long and random.
- In tests, inject `TimeProvider` with fixed clock for deterministic expiration checks.
