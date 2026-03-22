package com.infosung.atomic.spring.security.handler

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import tools.jackson.databind.ObjectMapper

class JwtAuthenticationEntryPointTest {
  private val objectMapper = ObjectMapper()

  @Test
  fun `entry point should return stable security unauthorized code`() {
    val entryPoint = JwtAuthenticationEntryPoint(objectMapper = objectMapper)
    val request = MockHttpServletRequest("GET", "/api/v1/private")
    val response = MockHttpServletResponse()

    entryPoint.commence(
        request,
        response,
        BadCredentialsException("invalid token"),
    )

    val body = objectMapper.readTree(response.contentAsString)

    assertEquals(401, response.status)
    assertEquals("application/json", response.contentType)
    assertEquals("SECURITY_UNAUTHORIZED", body["code"]?.stringValue())
    assertEquals("Unauthorized", body["message"]?.stringValue())
  }
}
