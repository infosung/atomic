package com.infosung.atomic.spring.web.ratelimit

import com.infosung.atomic.contract.time.TimeProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class RateLimitFilterTest {
  @Test
  fun `filter should pass first request and block over limit request`() {
    val store = InMemoryRateLimitStore()
    val timeProvider =
        TimeProvider().apply {
          configureClock(Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC))
        }
    val filter =
        RateLimitFilter(
            store = store,
            policyResolver =
                RateLimitPolicyResolver { RateLimitPolicy(limit = 1, windowSeconds = 60) },
            keyResolver = RateLimitKeyResolver { "member-1" },
            timeProvider = timeProvider,
            failOpen = false,
            includeMethods = setOf("GET"),
        )
    val chainCount = AtomicInteger(0)
    val chain = FilterChain { _, response ->
      chainCount.incrementAndGet()
      (response as HttpServletResponse).status = HttpServletResponse.SC_NO_CONTENT
    }

    val firstReq = MockHttpServletRequest("GET", "/api/v1/items")
    val firstRes = MockHttpServletResponse()
    filter.doFilter(firstReq, firstRes, chain)

    val secondReq = MockHttpServletRequest("GET", "/api/v1/items")
    val secondRes = MockHttpServletResponse()
    filter.doFilter(secondReq, secondRes, chain)

    assertEquals(1, chainCount.get())
    assertEquals(204, firstRes.status)
    assertEquals(429, secondRes.status)
    assertEquals("1", secondRes.getHeader("X-RateLimit-Limit"))
    assertEquals("0", secondRes.getHeader("X-RateLimit-Remaining"))
    assertTrue(secondRes.getHeader("Retry-After")?.toLong() ?: 0L > 0L)
  }

  @Test
  fun `filter should pass request when store fails and failOpen is true`() {
    val filter =
        RateLimitFilter(
            store = RateLimitStore { _, _, _ -> throw IllegalStateException("redis down") },
            policyResolver =
                RateLimitPolicyResolver { RateLimitPolicy(limit = 1, windowSeconds = 60) },
            keyResolver = RateLimitKeyResolver { "member-1" },
            failOpen = true,
            includeMethods = setOf("GET"),
        )
    val chainCount = AtomicInteger(0)
    val chain = FilterChain { _, response ->
      chainCount.incrementAndGet()
      (response as HttpServletResponse).status = HttpServletResponse.SC_OK
    }
    val request = MockHttpServletRequest("GET", "/api/v1/items")
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, chain)

    assertEquals(1, chainCount.get())
    assertEquals(200, response.status)
  }

  @Test
  fun `filter should reject request when actor key is missing by default`() {
    val filter =
        RateLimitFilter(
            store = InMemoryRateLimitStore(),
            policyResolver =
                PathPrefixRateLimitPolicyResolver(
                    defaultPolicy = RateLimitPolicy(limit = 10, windowSeconds = 60)),
            keyResolver = RateLimitKeyResolver { null },
            includeMethods = setOf("GET"),
        )
    val chainCount = AtomicInteger(0)
    val chain = FilterChain { _, _ -> chainCount.incrementAndGet() }
    val request = MockHttpServletRequest("GET", "/api/v1/items")
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, chain)

    assertEquals(400, response.status)
    assertEquals(0, chainCount.get())
    assertTrue(response.contentAsString.contains("Rate-limit key is missing"))
  }

  @Test
  fun `filter should allow missing actor key when policy is skip`() {
    val filter =
        RateLimitFilter(
            store = InMemoryRateLimitStore(),
            policyResolver =
                PathPrefixRateLimitPolicyResolver(
                    defaultPolicy = RateLimitPolicy(limit = 10, windowSeconds = 60)),
            keyResolver = RateLimitKeyResolver { null },
            includeMethods = setOf("GET"),
            missingKeyPolicy = RateLimitMissingKeyPolicy.SKIP,
        )
    val chainCount = AtomicInteger(0)
    val chain = FilterChain { _, _ -> chainCount.incrementAndGet() }
    val request = MockHttpServletRequest("GET", "/api/v1/items")
    val response = MockHttpServletResponse()

    filter.doFilter(request, response, chain)

    assertEquals(1, chainCount.get())
    assertEquals(200, response.status)
  }

  @Test
  fun `filter should share one bucket for rule-prefix matched paths by default`() {
    val store = InMemoryRateLimitStore()
    val filter =
        RateLimitFilter(
            store = store,
            policyResolver =
                PathPrefixRateLimitPolicyResolver(
                    defaultPolicy = RateLimitPolicy(limit = 10, windowSeconds = 60),
                    rules =
                        listOf(
                            RateLimitRule(
                                pathPrefix = "/api/v1/orders/",
                                methods = setOf("GET"),
                                policy = RateLimitPolicy(limit = 1, windowSeconds = 60),
                            ))),
            keyResolver = RateLimitKeyResolver { "member-1" },
            includeMethods = setOf("GET"),
            failOpen = false,
        )
    val chainCount = AtomicInteger(0)
    val chain = FilterChain { _, _ -> chainCount.incrementAndGet() }

    val first = MockHttpServletResponse()
    filter.doFilter(MockHttpServletRequest("GET", "/api/v1/orders/1"), first, chain)

    val second = MockHttpServletResponse()
    filter.doFilter(MockHttpServletRequest("GET", "/api/v1/orders/2"), second, chain)

    assertEquals(1, chainCount.get())
    assertEquals(200, first.status)
    assertEquals(429, second.status)
    assertFalse(second.getHeader("Retry-After").isNullOrBlank())
  }
}
