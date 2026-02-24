package com.infosung.atomic.spring.web.header

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.mock.web.MockHttpServletRequest

class ClientIpResolverTest {
  @Test
  fun `getClientIp should prefer forwarding headers`() {
    val request =
        MockHttpServletRequest("GET", "/v1/test").apply {
          addHeader("X-Forwarded-For", "192.168.0.77, 10.0.0.1")
          remoteAddr = "203.0.113.15"
        }

    assertEquals("192.168.0.77", request.getClientIp())
  }

  @Test
  fun `getClientIp should fallback to remoteAddr when forwarding headers are missing`() {
    val request = MockHttpServletRequest("GET", "/v1/test").apply { remoteAddr = "203.0.113.15" }

    assertEquals("203.0.113.15", request.getClientIp())
  }
}
