package com.infosung.atomic.spring.security.handler

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import tools.jackson.databind.ObjectMapper

class JwtAccessDeniedHandlerTest {
  private val objectMapper = ObjectMapper()

  @Test
  fun `access denied handler should return stable security forbidden code`() {
    val handler = JwtAccessDeniedHandler(objectMapper = objectMapper)
    val request = MockHttpServletRequest("GET", "/api/v1/admin")
    val response = MockHttpServletResponse()

    handler.handle(
        request,
        response,
        AccessDeniedException("forbidden"),
    )

    val body = objectMapper.readTree(response.contentAsString)

    assertEquals(403, response.status)
    assertEquals("application/json", response.contentType)
    assertEquals("SECURITY_FORBIDDEN", body["code"]?.stringValue())
    assertEquals("Forbidden", body["message"]?.stringValue())
  }
}
