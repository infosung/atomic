package com.infosung.atomic.contract.exception

import kotlin.test.Test
import kotlin.test.assertEquals

class HttpStatusExceptionTest {
  @Test
  fun `HttpUnauthorizedException should expose 401 status`() {
    val exception = HttpUnauthorizedException()
    assertEquals(401, exception.status)
    assertEquals("Unauthorized", exception.message)
  }

  @Test
  fun `HttpInvalidTokenException should expose 401 status`() {
    val exception = HttpInvalidTokenException("Token parsing failed.")
    assertEquals(401, exception.status)
    assertEquals("Token parsing failed.", exception.message)
  }

  @Test
  fun `HttpTokenNotExpiredException should expose 400 status`() {
    val exception = HttpTokenNotExpiredException()
    assertEquals(400, exception.status)
    assertEquals("Token is not expired yet.", exception.message)
  }
}
