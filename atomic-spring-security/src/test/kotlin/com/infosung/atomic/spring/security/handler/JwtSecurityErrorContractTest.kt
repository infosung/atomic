package com.infosung.atomic.spring.security.handler

import com.infosung.atomic.spring.security.SecurityErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import tools.jackson.databind.ObjectMapper

class JwtSecurityErrorContractTest {
  private val objectMapper = ObjectMapper()

  @Test
  fun `security error codes should remain stable`() {
    assertEquals(
        listOf(
            Triple("SECURITY_UNAUTHORIZED", 401, "Unauthorized"),
            Triple("SECURITY_FORBIDDEN", 403, "Forbidden"),
        ),
        SecurityErrorCode.entries.map { Triple(it.name, it.defaultHttpStatus, it.defaultMessage) },
    )
  }

  @Test
  fun `authentication entry point should return stable unauthorized code`() {
    val handler = JwtAuthenticationEntryPoint(objectMapper)
    val request = MockHttpServletRequest("GET", "/api/test")
    val response = MockHttpServletResponse()

    handler.commence(request, response, BadCredentialsException("bad credentials"))

    val body = objectMapper.readTree(response.contentAsString)
    assertEquals(HttpStatus.UNAUTHORIZED.value(), response.status)
    assertEquals("application/json", response.contentType)
    assertEquals(SecurityErrorCode.SECURITY_UNAUTHORIZED.name, body["code"].stringValue())
    assertEquals("Unauthorized", body["message"].stringValue())
  }

  @Test
  fun `access denied handler should return stable forbidden code`() {
    val handler = JwtAccessDeniedHandler(objectMapper)
    val request = MockHttpServletRequest("GET", "/api/test")
    val response = MockHttpServletResponse()

    handler.handle(request, response, AccessDeniedException("forbidden"))

    val body = objectMapper.readTree(response.contentAsString)
    assertEquals(HttpStatus.FORBIDDEN.value(), response.status)
    assertEquals("application/json", response.contentType)
    assertEquals(SecurityErrorCode.SECURITY_FORBIDDEN.name, body["code"].stringValue())
    assertEquals("Forbidden", body["message"].stringValue())
  }
}
