package com.infosung.atomic.spring.web.ratelimit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.springframework.mock.web.MockHttpServletRequest

class PathPrefixRateLimitPolicyResolverTest {
  @Test
  fun `resolver should follow first-match rule order`() {
    val resolver =
        PathPrefixRateLimitPolicyResolver(
            defaultPolicy = RateLimitPolicy(limit = 100, windowSeconds = 60),
            rules =
                listOf(
                    RateLimitRule(
                        pathPrefix = "/api/v1",
                        methods = setOf("GET"),
                        policy = RateLimitPolicy(limit = 30, windowSeconds = 60),
                    ),
                    RateLimitRule(
                        pathPrefix = "/api/v1/admin",
                        methods = setOf("GET"),
                        policy = RateLimitPolicy(limit = 10, windowSeconds = 60),
                    ),
                ),
        )
    val request = MockHttpServletRequest("GET", "/api/v1/admin/users")

    val policy = resolver.resolve(request)

    assertEquals(30, policy?.limit)
    assertEquals(60, policy?.windowSeconds)
  }

  @Test
  fun `resolver should return null for excluded path`() {
    val resolver =
        PathPrefixRateLimitPolicyResolver(
            defaultPolicy = RateLimitPolicy(limit = 100, windowSeconds = 60),
            excludedPathPrefixes = setOf("/actuator"),
        )
    val request = MockHttpServletRequest("GET", "/actuator/health")

    val policy = resolver.resolve(request)

    assertNull(policy)
  }

  @Test
  fun `resolver should use rule-prefix key by default`() {
    val resolver =
        PathPrefixRateLimitPolicyResolver(
            defaultPolicy = RateLimitPolicy(limit = 100, windowSeconds = 60),
            rules =
                listOf(
                    RateLimitRule(
                        pathPrefix = "/api/v1/orders/",
                        methods = setOf("GET"),
                        policy = RateLimitPolicy(limit = 10, windowSeconds = 60),
                    ),
                ),
        )
    val request = MockHttpServletRequest("GET", "/api/v1/orders/123")

    val pathKey = resolver.resolvePathKey(request)

    assertEquals("/api/v1/orders/", pathKey)
  }

  @Test
  fun `resolver should support request-uri key strategy`() {
    val resolver =
        PathPrefixRateLimitPolicyResolver(
            defaultPolicy = RateLimitPolicy(limit = 100, windowSeconds = 60),
            pathKeyStrategy = RateLimitPathKeyStrategy.REQUEST_URI,
        )
    val request = MockHttpServletRequest("GET", "/api/v1/orders/123")

    val pathKey = resolver.resolvePathKey(request)

    assertEquals("/api/v1/orders/123", pathKey)
  }

  @Test
  fun `resolver should not overmatch sibling path prefixes`() {
    val resolver =
        PathPrefixRateLimitPolicyResolver(
            defaultPolicy = RateLimitPolicy(limit = 100, windowSeconds = 60),
            rules =
                listOf(
                    RateLimitRule(
                        pathPrefix = "/api/v1",
                        methods = setOf("GET"),
                        policy = RateLimitPolicy(limit = 10, windowSeconds = 60),
                    ),
                ),
        )
    val request = MockHttpServletRequest("GET", "/api/v10/orders")

    val policy = resolver.resolve(request)
    val pathKey = resolver.resolvePathKey(request)

    assertNotNull(policy)
    assertEquals(100, policy.limit)
    assertEquals("default", pathKey)
  }
}
