# atomic.spring.web Guide

## Summary

`atomic.spring.web` provides:

- API request/response logging pipeline
- Rest client interceptor and error handler
- shared web exception handling base
- header/locale helpers

## Required Beans (Typical)

To use API logging and exception handling, register these:

1. `JsonTransfer`
2. `LogSaver` implementation
3. `ServiceLogger`
4. `ApiLogFilter` (+ `FilterRegistrationBean`)
5. `ApiLogAspect` subclass
6. `BaseExceptionHandler` subclass

## Example Configuration

```kotlin
import com.infosung.atomic.contract.time.TimeProvider
import com.infosung.atomic.spring.web.exception.BaseExceptionHandler
import com.infosung.atomic.spring.web.json.JsonTransfer
import com.infosung.atomic.spring.web.log.*
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.annotation.Aspect
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.bind.annotation.RestControllerAdvice
import tools.jackson.databind.ObjectMapper

@Configuration
class AtomicWebConfig {
  @Bean
  fun timeProvider() = TimeProvider()

  @Bean
  fun jsonTransfer(objectMapper: ObjectMapper) = JsonTransfer(objectMapper = objectMapper)

  @Bean
  fun logSaver(): LogSaver = object : LogSaver {
    override fun saveAll(logs: List<ServiceLog>) {
      // persist to DB, queue, or log system
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
  override fun logging(log: ServiceLog) {
    // optional additional side effect
  }

  override fun getUserId(): Any? = null

  override fun resolveRequestFromContext(): HttpServletRequest? =
      (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
}

@RestControllerAdvice
class AppExceptionHandler(
    environment: Environment
) : BaseExceptionHandler(environment = environment) {
  override fun alert(e: Exception, message: String) {
    // send notification if needed
  }
}
```

## RestClient Integration

```kotlin
import com.infosung.atomic.spring.web.RestClientErrorHandler
import com.infosung.atomic.spring.web.RestClientInterceptor
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class RestClientConfig {
  @Bean
  fun restTemplate(builder: RestTemplateBuilder): RestTemplate =
      builder
          .additionalInterceptors(RestClientInterceptor())
          .errorHandler(RestClientErrorHandler())
          .build()
}
```

## Locale Resolution

```kotlin
import com.infosung.atomic.spring.web.locale.SupportedLocaleResolver
import java.util.Locale

val supported = listOf(Locale.KOREAN, Locale.ENGLISH)
val resolved = SupportedLocaleResolver.resolveSupportedLocale(request, supported, Locale.ENGLISH)
// resolved.locale / resolved.code / resolved.displayName
```
