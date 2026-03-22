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
- centralize shared HTTP mapping for `atomic.app.*` modules

Required:

- either the shared Atomic web exception handler
  - the built-in `AtomicHttpExceptionHandler` is scoped to `com.infosung.atomic.app` controllers only
  - the built-in handler does not send alerts; register your own `BaseExceptionHandler` subclass if you need Slack/webhook/email integration
  - if you want one app-wide exception policy for your own controllers too, register your own `@RestControllerAdvice` subclass of `BaseExceptionHandler`
  - your own `BaseExceptionHandler` advice does not suppress the built-in Atomic advice by itself; the built-in handler stays registered for `atomic.app.*` unless you explicitly replace it
  - if both your advice and the built-in advice match the same controller, normal Spring advice ordering decides which one actually handles the exception
  - if you intentionally want to replace the built-in scoped handler, implement `AtomicHttpExceptionHandlerReplacement` on your `BaseExceptionHandler` advice
  - do not rely on plain advice ordering to replace the built-in scoped handler; replacement is only deterministic through `AtomicHttpExceptionHandlerReplacement`
- or your subclass of `BaseExceptionHandler`

Optional:

- custom `alert(...)` integration (Slack/webhook)
  - the built-in `AtomicHttpExceptionHandler` still sends no alerts because it disables alert delivery through `shouldAlert(...) = false`
  - your own `BaseExceptionHandler` subclass owns its alert policy, including production behavior
- `BaseExceptionHandler` customization hooks
  - override `shouldAlert(...)` if you want to suppress or narrow alert delivery without changing response mapping
  - override `createErrorResponse(...)` if you want to customize the response envelope without reimplementing every exception handler method

Recommended direction:

- atomic app modules expose public typed exceptions for host/use-case integration
- web adapters translate transport failures into `HttpStatusException`
- your global web exception layer should branch on `HttpStatusException.code` or typed module exceptions, not message text

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

- `ApiLogAspect` trace logs avoid full header/body dumps and keep metadata only (`method`, `uri`, `traceId`, payload presence flags).
- request query/body payloads are persisted through `JsonTransfer` with sensitive-key masking.
- keep `TRACE` off in production for `ServiceLogger` unless required for incident response.

### C) Outbound HTTP Standardization

Purpose:

- convert outbound `RestTemplate` errors to module-defined exception behavior
- log upstream error metadata (`status`, `bodyLength`) with sanitized URL (query/fragment removed) and without raw error body output

Required:

- `RestClientInterceptor`
- `RestClientErrorHandler`

### D) Header/Locale Helpers

Purpose:

- normalize header DTO, request language hint extraction, and supported locale resolution

No bean required:

- `toHeaderDto(...)`
- `getClientIp()`
- `RequestHeaderReader`
- `RequestLanguageResolver`
- `SupportedLocaleResolver`

Header/language helper guidance:

- Use `RequestHeaderReader.getCustomLanguage(...)` when you need the raw inbound header helper.
  This keeps `X-Custom-Language` first and otherwise returns raw `Accept-Language`.
- Use `RequestHeaderReader.getPreferredLanguageTag(...)` or `RequestLanguageResolver.resolvePreferredLanguageTag(...)`
  when you need one request language hint string.
  This keeps `X-Custom-Language` first and otherwise falls back to the first Servlet-preferred locale
  from `HttpServletRequest.getLocales()`.
- The returned value is a canonical language tag when the input is usable.
- This helper is request hint extraction only.
  It does not perform supported-locale selection or domain policy resolution.
- Use `SupportedLocaleResolver` only after your service has decided how to match supported locales.

Quick selection guide:

| Need | Use |
|---|---|
| raw inbound header value | `RequestHeaderReader.getCustomLanguage(...)` |
| one canonical request language hint | `RequestHeaderReader.getPreferredLanguageTag(...)` or `RequestLanguageResolver.resolvePreferredLanguageTag(...)` |
| supported locale matching | `SupportedLocaleResolver.resolveSupportedLocale(...)` |

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

Stable non-MVC error codes:

| Code | Status | Default message | Emitted when |
|---|---:|---|---|
| `RATE_LIMIT_KEY_REQUIRED` | `400` | `Rate-limit key is missing.` | the key resolver returns no usable actor and `missingKeyPolicy=REJECT` |
| `RATE_LIMIT_EXCEEDED` | `429` | `Too many requests.` | a request is over the configured threshold |

Rate-limit rejection paths now return JSON `BaseResponse` payloads with those stable codes.
`atomic.web.rate-limit.response-body` still controls the user-facing throttle message, but it now
fills the JSON `message` field rather than a plain text body.

Unexpected rate-limit faults are still implementor-owned. In practice, review and handle failures
from:

- `RateLimitPolicyResolver.resolve(...)`
- `RateLimitKeyResolver.resolve(...)`
- `RateLimitStore.consume(...)` when `failOpen=false`
- response serialization / servlet writer failures

`failOpen=true` only covers store consumption failures. It does not flatten resolver failures or
unexpected servlet/runtime faults into a stable Atomic `500` response.

## Feature-to-Bean Matrix

| Feature | Required Beans/Classes | Optional |
|---|---|---|
| Exception handling | built-in `AtomicHttpExceptionHandler` for `atomic.app.*`, or your `BaseExceptionHandler` subclass for broader/custom policy | alert integration |
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
import com.infosung.atomic.spring.web.exception.AtomicHttpExceptionHandlerReplacement
import com.infosung.atomic.spring.web.exception.BaseExceptionHandler
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AppExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment) {
  override fun shouldAlert(
      e: Exception,
      request: HttpServletRequest,
      status: Int,
  ): Boolean = status >= 500

  override fun alert(e: Exception, message: String) {
    // optional alert integration
  }
}
```

If you want that advice to replace the built-in scoped `AtomicHttpExceptionHandler`, implement
`AtomicHttpExceptionHandlerReplacement` too:

```kotlin
@RestControllerAdvice
class GlobalAppExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment), AtomicHttpExceptionHandlerReplacement {
  override fun shouldAlert(
      e: Exception,
      request: HttpServletRequest,
      status: Int,
  ): Boolean = status >= 500

  override fun alert(e: Exception, message: String) {
    // optional alert integration
  }
}
```

If you want to keep the default handler methods but shape the response envelope differently, override
`createErrorResponse(...)`:

```kotlin
import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.spring.web.exception.BaseExceptionHandler
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AppExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment = environment) {
  override fun createErrorResponse(
      e: Exception,
      status: Int,
  ): BaseResponse<Any> {
    return BaseResponse(
        code = "HOST_OVERRIDE_CODE",
        message = "Host override response",
    )
  }

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
import com.infosung.atomic.spring.web.header.RequestLanguageResolver
import com.infosung.atomic.spring.web.header.toHeaderDto
import com.infosung.atomic.spring.web.locale.SupportedLocaleResolver
import java.util.Locale

val headerDto = request.toHeaderDto()
val preferredLanguageTag = RequestLanguageResolver.resolvePreferredLanguageTag(request)

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
- If upstream error payload analysis is required, handle `HttpRemoteCallException.responseBody` in application code; module logs keep only metadata (`status`, `bodyLength`).
- `HttpRemoteCallException.url` and `HttpRequestExecutionException.url` fields keep original request URL; treat these fields as sensitive when logging/alerting.
- Exception messages for those classes are sanitized and do not include raw URL query values.

## Troubleshooting

- API logs missing response logs: `ApiLogFilter` missing or not registered.
- API logs not persisted: `serviceLogger.send()` not invoked.
- Upstream 4xx/5xx response body is not visible in logs by design: check `HttpRemoteCallException.responseBody` at handling boundary.
- Exception response not standardized: `BaseExceptionHandler` subclass not active.
- When `BaseExceptionHandler` is active, `5xx` responses are masked to `Internal Server Error` by default.
- Duplicate logs: filter registered multiple times.
- Unexpected `429`: verify `atomic.web.rate-limit.include-methods`, key strategy, and rule/path matching.
- Unexpected global throttling across endpoints: either define explicit `rules` per prefix or switch `path-key-strategy=request-uri`.
- Unexpected `400` with header key strategy: verify configured key header is always present or set `missing-key-policy=skip`.
- Need machine-readable rate-limit branching: map on `RATE_LIMIT_KEY_REQUIRED` / `RATE_LIMIT_EXCEEDED`.
