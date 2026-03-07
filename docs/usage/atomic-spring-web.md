# atomic.spring.web Guide

## Why Use This Module

Use `atomic.spring.web` when your service needs one or more of these:

- consistent exception-to-response mapping
- API request/response audit logging
- request rate-limit filter
- outbound HTTP failure normalization
- header/locale parsing helpers

You do not need to register everything.
Enable only the feature packs you use.

## Prerequisites

- Spring MVC app
- Tested with Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.3`
- `atomic.contract` dependency

## Quick Start (First Day)

1. Enable Exception Handling first.
2. Add API logging only if you need audit logs.
3. Add outbound HTTP handlers only if you use `RestTemplate`.
4. Add rate-limit filter if endpoint protection is required.
5. Add helper utilities (`toHeaderDto`, locale resolver) where needed.

Property reference:
- Full property index (default / required condition / description): [Property Reference by Module](environment-variables.md) -> `atomic.web`

## Feature Packs

### A) Exception Handling

Purpose:

- convert `HttpStatusException` and common exceptions to consistent `BaseResponse`

Required:

- your subclass of `BaseExceptionHandler`

Optional:

- custom `alert(...)` integration (Slack/webhook)

### B) API Logging (Request/Response)

Purpose:

- request/response paired log with trace id, latency, endpoint, status

Required as a set:

- `JsonTransfer`
- `LogSaver` implementation
- `ServiceLogger`
- `ApiLogFilter` (+ filter registration)
- your subclass of `ApiLogAspect`

Optional:

- `TimeProvider`
- `TraceIdGenerator`

Important runtime rule:

- `ServiceLogger` queues logs.
- persistence happens when `serviceLogger.send()` runs.

Logging safety notes:

- `ApiLogAspect` trace logs do not print raw request body/header values directly.
- request query/body payloads are persisted through `JsonTransfer` with sensitive-key masking.

### C) Outbound HTTP Standardization

Purpose:

- convert outbound `RestTemplate` errors to module-defined exception behavior
- log upstream error metadata (`status`, `bodyLength`, `bodySha256`) without raw error body output

Required:

- `RestClientInterceptor`
- `RestClientErrorHandler`

### D) Header/Locale Helpers

Purpose:

- normalize header DTO and supported locale resolution

No bean required:

- `toHeaderDto(...)`
- `getClientIp()`
- `RequestHeaderReader`
- `SupportedLocaleResolver`

### E) Rate-limit Filter

Purpose:

- protect endpoints by request rate thresholds (per IP/header key)

Required:

- `RateLimitStore`
- `RateLimitPolicyResolver`
- `RateLimitKeyResolver`
- `RateLimitFilter`

Recommended with starter:

- enable `atomic.web.rate-limit.enabled=true` and use starter auto-config
- default `store=auto` selects Redis when `StringRedisTemplate` exists, else in-memory

## Feature-to-Bean Matrix

| Feature | Required Beans/Classes | Optional |
|---|---|---|
| Exception handling | `BaseExceptionHandler` subclass | alert integration |
| API logging | `JsonTransfer`, `LogSaver`, `ServiceLogger`, `ApiLogFilter`, `ApiLogAspect` subclass | `TimeProvider`, `TraceIdGenerator` |
| Rate-limit | `RateLimitStore`, `RateLimitPolicyResolver`, `RateLimitKeyResolver`, `RateLimitFilter` | Redis store or custom store |
| Outbound HTTP (`RestTemplate`) | `RestClientInterceptor`, `RestClientErrorHandler` | custom rest policies |
| Header/locale helpers | none | none |

## Dependency Relationship (API Logging)

```text
ApiLogAspect
  -> JsonTransfer
  -> writes request log to ApiLogContext

ApiLogFilter
  -> ServiceLogger
  -> reads ApiLogContext and writes response log

ServiceLogger
  -> LogSaver
  -> send() triggers actual persistence
```

If one of `ApiLogAspect` or `ApiLogFilter` is missing, paired request/response logging is incomplete.

## Quick Config: Exception Handling Only

```kotlin
import com.infosung.atomic.spring.web.exception.BaseExceptionHandler
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AppExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment) {
  override fun alert(e: Exception, message: String) {
    // optional alert integration
  }
}
```

## Quick Config: API Logging Set

```kotlin
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.web.json.JsonTransfer
import com.infosung.atomic.spring.web.log.ApiLogAspect
import com.infosung.atomic.spring.web.log.ApiLogFilter
import com.infosung.atomic.spring.web.log.LogSaver
import com.infosung.atomic.spring.web.log.ServiceLog
import com.infosung.atomic.spring.web.log.ServiceLogger
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import tools.jackson.databind.ObjectMapper

@Configuration
class AtomicWebLoggingConfig {
  @Bean
  fun timeProvider() = TimeProvider()

  @Bean
  fun jsonTransfer(objectMapper: ObjectMapper) = JsonTransfer(objectMapper = objectMapper)

  @Bean
  fun logSaver(): LogSaver = object : LogSaver {
    override fun saveAll(logs: List<ServiceLog>) {
      // persist to DB / queue / observability backend
    }
  }

  @Bean
  fun serviceLogger(logSaver: LogSaver) = ServiceLogger(logSaver = logSaver)

  @Bean
  fun apiLogFilter(serviceLogger: ServiceLogger, timeProvider: TimeProvider) =
      ApiLogFilter(logger = serviceLogger, timeProvider = timeProvider)

  @Bean
  fun apiLogFilterRegistration(apiLogFilter: ApiLogFilter) =
      FilterRegistrationBean(apiLogFilter).apply {
        order = 1
        addUrlPatterns("/*")
      }
}

@Aspect
@Component
class AppApiLogAspect(
    jsonTransfer: JsonTransfer,
    timeProvider: TimeProvider,
) : ApiLogAspect(jsonTransfer = jsonTransfer, timeProvider = timeProvider) {
  override fun logging(log: ServiceLog) {}

  override fun getUserId(): Any? = null

  override fun resolveRequestFromContext(): HttpServletRequest? =
      (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
}
```

## Required When API Logging Is Enabled: send() Trigger

```kotlin
import com.infosung.atomic.spring.web.log.ServiceLogger
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@EnableScheduling
@Component
class ApiLogFlushScheduler(
    private val serviceLogger: ServiceLogger,
) {
  @Scheduled(fixedDelay = 1_000)
  fun flush() {
    serviceLogger.send()
  }
}
```

## Quick Config: Outbound HTTP (`RestTemplate`)

```kotlin
import com.infosung.atomic.spring.web.RestClientErrorHandler
import com.infosung.atomic.spring.web.RestClientInterceptor
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class OutboundHttpConfig {
  @Bean
  fun restTemplate(builder: RestTemplateBuilder): RestTemplate =
      builder
          .additionalInterceptors(RestClientInterceptor())
          .errorHandler(RestClientErrorHandler())
          .build()
}
```

## Quick Config: Rate-limit Filter

```yaml
atomic:
  web:
    rate-limit:
      enabled: true
      store: auto # auto, in-memory, redis, custom
      limit: 100
      window-seconds: 60
      include-methods: [GET, POST, PUT, PATCH, DELETE]
      exclude-path-prefixes: [/actuator]
      path-key-strategy: rule-prefix # rule-prefix, request-uri
      key-strategy: ip # ip, header
      ip:
        trust-forwarded-headers: false
      key-header-name: X-User-Id
      missing-key-policy: reject # reject, skip
      fail-open: true
      response-body: Too many requests.
      in-memory:
        cleanup-interval: 1000
      redis:
        key-prefix: atomic:ratelimit:
      filter:
        order: -100
        url-patterns:
          - /*
```

Manual bean registration example (without starter):

```kotlin
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.web.ratelimit.InMemoryRateLimitStore
import com.infosung.atomic.spring.web.ratelimit.IpRateLimitKeyResolver
import com.infosung.atomic.spring.web.ratelimit.PathPrefixRateLimitPolicyResolver
import com.infosung.atomic.spring.web.ratelimit.RateLimitFilter
import com.infosung.atomic.spring.web.ratelimit.RateLimitPolicy
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RateLimitConfig {
  @Bean
  fun rateLimitFilter() =
      RateLimitFilter(
          store = InMemoryRateLimitStore(),
          policyResolver = PathPrefixRateLimitPolicyResolver(defaultPolicy = RateLimitPolicy(100, 60)),
          keyResolver = IpRateLimitKeyResolver(),
          timeProvider = TimeProvider(),
      )

  @Bean
  fun rateLimitFilterRegistration(filter: RateLimitFilter) =
      FilterRegistrationBean(filter).apply {
        order = -100
        addUrlPatterns("/*")
      }
}
```

## Optional Helper Usage

```kotlin
import com.infosung.atomic.spring.web.header.toHeaderDto
import com.infosung.atomic.spring.web.locale.SupportedLocaleResolver
import java.util.Locale

val headerDto = request.toHeaderDto()

val resolved =
    SupportedLocaleResolver.resolveSupportedLocale(
        request = request,
        supportedLocales = listOf(Locale.KOREAN, Locale.ENGLISH),
        defaultLocale = Locale.ENGLISH,
    )
```

## Operational Checklist

- Register only feature packs you actually use.
- If using API logging, define and test `ServiceLogger.send()` schedule/policy.
- Set `atomic.web.json.sensitive-key-pattern` for your domain-specific secret keys.
- Verify `FilterRegistrationBean` order does not conflict with other filters.
- For rate-limit with multi-instance deployment, use Redis/custom shared store instead of in-memory.
- Any user-defined `RateLimitStore` bean overrides starter-provided store (`@ConditionalOnMissingBean`).
- Rate-limit rule matching is first-match wins.
- `X-RateLimit-Reset` and `Retry-After` are based on seconds until current fixed-window boundary.
- Default `path-key-strategy=rule-prefix` avoids path-variable sharding; use `request-uri` for legacy per-URI buckets.
- `path-prefix` and `exclude-path-prefixes` match exact prefix boundary (`/api/v1` matches `/api/v1` and `/api/v1/...`, not `/api/v10`).
- Effective rate-limit key is `actor|method|pathKey`; with default `rule-prefix`, unmatched routes use `pathKey=default` and share one bucket.
- Default `key-strategy=ip` uses `remoteAddr`; enable `ip.trust-forwarded-headers=true` only behind trusted proxy/ingress that sanitizes forwarding headers.
- Ensure exception handler is in component scan scope.
- If upstream error payload analysis is required, handle `HttpRemoteCallException.responseBody` in application code; module logs keep only body hash/length.

## Troubleshooting

- API logs missing response logs: `ApiLogFilter` missing or not registered.
- API logs not persisted: `serviceLogger.send()` not invoked.
- Upstream 4xx/5xx response body is not visible in logs by design: check `HttpRemoteCallException.responseBody` at handling boundary.
- Exception response not standardized: `BaseExceptionHandler` subclass not active.
- Duplicate logs: filter registered multiple times.
- Unexpected `429`: verify `atomic.web.rate-limit.include-methods`, key strategy, and rule/path matching.
- Unexpected global throttling across endpoints: either define explicit `rules` per prefix or switch `path-key-strategy=request-uri`.
- Unexpected `400` with header key strategy: verify configured key header is always present or set `missing-key-policy=skip`.
