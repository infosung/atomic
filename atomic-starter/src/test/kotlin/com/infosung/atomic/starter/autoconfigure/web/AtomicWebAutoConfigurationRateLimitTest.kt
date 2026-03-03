package com.infosung.atomic.starter.autoconfigure.web

import com.infosung.atomic.spring.web.ratelimit.InMemoryRateLimitStore
import com.infosung.atomic.spring.web.ratelimit.RateLimitFilter
import com.infosung.atomic.spring.web.ratelimit.RateLimitKeyResolver
import com.infosung.atomic.spring.web.ratelimit.RateLimitStore
import jakarta.servlet.FilterChain
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.mockito.Mockito
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class AtomicWebAutoConfigurationRateLimitTest {
  private val contextRunner =
      ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AtomicWebAutoConfiguration::class.java))

  @Test
  fun `auto store should fallback to in-memory when redis is unavailable`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.store=auto",
        )
        .run { context ->
          assertIs<InMemoryRateLimitStore>(context.getBean(RateLimitStore::class.java))
          assertIs<RateLimitFilter>(context.getBean(RateLimitFilter::class.java))
        }
  }

  @Test
  fun `auto store should select redis-backed store when redis template exists`() {
    contextRunner
        .withUserConfiguration(RedisTemplateConfiguration::class.java)
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.store=auto",
            "atomic.web.rate-limit.redis.key-prefix=atomic:ratelimit:test:",
        )
        .run { context ->
          val store = context.getBean(RateLimitStore::class.java)
          assertEquals(
              "com.infosung.atomic.starter.autoconfigure.web.RedisRateLimitStore",
              store::class.java.name,
          )
        }
  }

  @Test
  fun `custom store should use user provided bean`() {
    contextRunner
        .withUserConfiguration(CustomRateLimitStoreConfiguration::class.java)
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.store=custom",
        )
        .run { context ->
          assertIs<CustomRateLimitStoreConfiguration.CustomStore>(
              context.getBean(RateLimitStore::class.java))
        }
  }

  @Test
  fun `rate limit filter registration should run before idempotency by default`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
        )
        .run { context ->
          val registration = context.getBean("rateLimitFilterRegistration")
          assertIs<FilterRegistrationBean<*>>(registration)
          assertEquals(-100, registration.order)
        }
  }

  @Test
  fun `header key strategy should reject request when header is missing by default`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.key-strategy=header",
            "atomic.web.rate-limit.key-header-name=X-Actor-Id",
        )
        .run { context ->
          val filter = context.getBean(RateLimitFilter::class.java)
          val chainCalls = AtomicInteger(0)
          val chain = FilterChain { _, _ -> chainCalls.incrementAndGet() }
          val request = MockHttpServletRequest("GET", "/api/v1/items")
          val response = MockHttpServletResponse()

          filter.doFilter(request, response, chain)

          assertEquals(400, response.status)
          assertEquals(0, chainCalls.get())
        }
  }

  @Test
  fun `ip key strategy should use remote address by default`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.key-strategy=ip",
        )
        .run { context ->
          val keyResolver = context.getBean(RateLimitKeyResolver::class.java)
          val request = MockHttpServletRequest("GET", "/api/v1/items")
          request.addHeader("X-Forwarded-For", "198.51.100.10")
          request.remoteAddr = "203.0.113.15"

          val actor = keyResolver.resolve(request)

          assertEquals("203.0.113.15", actor)
        }
  }

  @Test
  fun `ip key strategy should trust forwarded header when explicitly enabled`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.key-strategy=ip",
            "atomic.web.rate-limit.ip.trust-forwarded-headers=true",
        )
        .run { context ->
          val keyResolver = context.getBean(RateLimitKeyResolver::class.java)
          val request = MockHttpServletRequest("GET", "/api/v1/items")
          request.addHeader("X-Forwarded-For", "198.51.100.10")
          request.remoteAddr = "203.0.113.15"

          val actor = keyResolver.resolve(request)

          assertEquals("198.51.100.10", actor)
        }
  }

  @Test
  fun `blank include methods should fail context startup`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.include-methods[0]= ",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertEquals(
              true,
              failure.message?.contains("atomic.web.rate-limit.include-methods"),
          )
        }
  }

  @Test
  fun `blank redis key prefix should fail when store is auto`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.store=auto",
            "atomic.web.rate-limit.redis.key-prefix= ",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertEquals(true, failure.message?.contains("atomic.web.rate-limit.redis.key-prefix"))
        }
  }

  @Test
  fun `blank redis key prefix should not fail when store is in-memory`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.store=in-memory",
            "atomic.web.rate-limit.redis.key-prefix= ",
        )
        .run { context ->
          assertEquals(null, context.startupFailure)
          assertIs<RateLimitStore>(context.getBean(RateLimitStore::class.java))
        }
  }

  @Test
  fun `mixed blank filter url patterns should fail context startup`() {
    contextRunner
        .withPropertyValues(
            "atomic.web.enabled=true",
            "atomic.web.rate-limit.enabled=true",
            "atomic.web.rate-limit.filter.url-patterns[0]=/*",
            "atomic.web.rate-limit.filter.url-patterns[1]= ",
        )
        .run { context ->
          val failure = context.startupFailure
          assertNotNull(failure)
          assertEquals(true, failure.message?.contains("filter.url-patterns"))
        }
  }

  @Configuration
  class CustomRateLimitStoreConfiguration {
    class CustomStore : RateLimitStore by InMemoryRateLimitStore()

    @Bean fun customRateLimitStore(): RateLimitStore = CustomStore()
  }

  @Configuration
  class RedisTemplateConfiguration {
    @Bean
    fun stringRedisTemplate(): StringRedisTemplate =
        StringRedisTemplate(Mockito.mock(RedisConnectionFactory::class.java))
  }
}
