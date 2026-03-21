package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.exception.HttpUnauthorizedException
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest

class BaseExceptionHandlerTest {
  @Test
  fun `httpStatusException should map status and payload`() {
    val handler = TestExceptionHandler()
    val request = MockHttpServletRequest("GET", "/v1/test")
    val exception = HttpUnauthorizedException()

    val response = handler.httpStatusException(exception, request)

    assertEquals(401, response.statusCode.value())
    assertEquals("HttpUnauthorizedException", response.body?.code)
    assertEquals("Unauthorized", response.body?.message)
  }

  @Test
  fun `httpStatusException should prefer explicit stable code`() {
    val handler = TestExceptionHandler()
    val request = MockHttpServletRequest("GET", "/v1/test")
    val exception =
        HttpStatusException(
            status = 404,
            code = "VERSION_POLICY_NOT_FOUND",
            message = "No version policy found.",
        )

    val response = handler.httpStatusException(exception, request)

    assertEquals(404, response.statusCode.value())
    assertEquals("VERSION_POLICY_NOT_FOUND", response.body?.code)
    assertEquals("No version policy found.", response.body?.message)
  }

  @Test
  fun `exception should mask 500 message`() {
    val handler = TestExceptionHandler()
    val request = MockHttpServletRequest("GET", "/v1/test")
    val exception = IllegalStateException("internal-detail")

    val response = handler.exception(exception, request)

    assertEquals(500, response.statusCode.value())
    assertEquals("IllegalStateException", response.body?.code)
    assertEquals("Internal Server Error", response.body?.message)
  }

  @Test
  fun `httpStatusException should keep stable code while masking 500 message`() {
    val handler = TestExceptionHandler()
    val request = MockHttpServletRequest("GET", "/v1/test")
    val exception =
        HttpStatusException(
            status = 500,
            code = "OAUTH_PROVIDER_REMOTE_FAILURE",
            message = "Failed to exchange provider token.",
        )

    val response = handler.httpStatusException(exception, request)

    assertEquals(500, response.statusCode.value())
    assertEquals("OAUTH_PROVIDER_REMOTE_FAILURE", response.body?.code)
    assertEquals("Internal Server Error", response.body?.message)
  }

  private class TestExceptionHandler :
      BaseExceptionHandler(
          environment = MockEnvironment(),
      ) {
    override fun alert(
        e: Exception,
        message: String,
    ) {}
  }
}
