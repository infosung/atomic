package com.infosung.atomic.spring.web.ratelimit

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.mock.web.MockHttpServletRequest

class RateLimitKeyResolverTest {
  @Test
  fun `ip resolver should use remote address by default`() {
    val request =
        MockHttpServletRequest("GET", "/api/v1/items").apply {
          addHeader("X-Forwarded-For", "198.51.100.10")
          remoteAddr = "203.0.113.15"
        }

    val actor = IpRateLimitKeyResolver().resolve(request)

    assertEquals("203.0.113.15", actor)
  }

  @Test
  fun `ip resolver should use forwarded header when trustForwardedHeaders is enabled`() {
    val request =
        MockHttpServletRequest("GET", "/api/v1/items").apply {
          addHeader("X-Forwarded-For", "198.51.100.10, 10.0.0.1")
          remoteAddr = "203.0.113.15"
        }

    val actor = IpRateLimitKeyResolver(trustForwardedHeaders = true).resolve(request)

    assertEquals("198.51.100.10", actor)
  }
}
