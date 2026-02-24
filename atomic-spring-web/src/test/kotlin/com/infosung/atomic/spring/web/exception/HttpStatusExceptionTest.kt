package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpStatusExceptionTest {
  @Test
  fun `HttpStatusException should keep message and cause`() {
    val cause = IllegalArgumentException("bad input")
    val exception = HttpStatusException(status = 418, message = "teapot", cause = cause)

    assertEquals(418, exception.status)
    assertEquals("teapot", exception.message)
    assertEquals(cause, exception.cause)
  }

  @Test
  fun `HttpRemoteCallException should expose status and context`() {
    val exception =
        HttpRemoteCallException(
            status = 404,
            method = "GET",
            url = "https://example.com/resource",
            responseBody = """{"code":"NOT_FOUND"}""",
        )

    assertEquals(404, exception.status)
    assertEquals("GET", exception.method)
    assertEquals("https://example.com/resource", exception.url)
    assertEquals("""{"code":"NOT_FOUND"}""", exception.responseBody)
  }

  @Test
  fun `HttpRequestExecutionException should default to status 500`() {
    val exception = HttpRequestExecutionException(method = "POST", url = "https://example.com")

    assertEquals(500, exception.status)
    assertEquals("POST", exception.method)
    assertEquals("https://example.com", exception.url)
  }

  @Test
  fun `HttpFilterProcessingException should clamp status below 400 to 500`() {
    val exception = HttpFilterProcessingException(method = "GET", uri = "/v1/test", status = 200)

    assertEquals(500, exception.status)
    assertEquals("GET", exception.method)
    assertEquals("/v1/test", exception.uri)
  }
}
