package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.exception.HttpUnauthorizedException
import com.infosung.atomic.contract.response.BaseResponse
import jakarta.servlet.http.HttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.bind.MissingServletRequestParameterException

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

  @Test
  fun `missing request parameter should map to stable 400 client error`() {
    val handler = TestExceptionHandler()
    val request = MockHttpServletRequest("DELETE", "/api/v1/storage/image/svc/S3")
    val exception = MissingServletRequestParameterException("imageId", "String")

    val response = handler.missingServletRequestParameterException(exception, request)

    assertEquals(400, response.statusCode.value())
    assertEquals("MISSING_REQUEST_PARAMETER", response.body?.code)
    assertEquals("Required request parameter 'imageId' is missing.", response.body?.message)
  }

  @Test
  fun `alertMessage should delegate alerts even in prod profile`() {
    val handler =
        RecordingExceptionHandler(
            environment = MockEnvironment().withProperty("spring.profiles.active", "prod"))
    val request = MockHttpServletRequest("GET", "/v1/test")

    handler.alertMessage(IllegalStateException("boom"), request)

    assertEquals(1, handler.alertCalls)
    assertTrue(handler.lastAlertMessage?.contains("GET /v1/test") == true)
  }

  @Test
  fun `httpStatusException should allow host subclasses to customize error response in one place`() {
    val handler = CustomResponseExceptionHandler()
    val request = MockHttpServletRequest("GET", "/v1/test")
    val exception =
        HttpStatusException(
            status = 404,
            code = "VERSION_POLICY_NOT_FOUND",
            message = "No version policy found.",
        )

    val response = handler.httpStatusException(exception, request)

    assertEquals(404, response.statusCode.value())
    assertEquals("HOST_OVERRIDE_CODE", response.body?.code)
    assertEquals("Host override response", response.body?.message)
  }

  @Test
  fun `exception should skip alert delivery when handler disables alerts`() {
    val handler = NoAlertExceptionHandler()
    val request = MockHttpServletRequest("GET", "/v1/test")

    handler.exception(IllegalStateException("boom"), request)

    assertEquals(0, handler.alertCalls)
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

  private class RecordingExceptionHandler(
      environment: MockEnvironment,
  ) : BaseExceptionHandler(environment = environment) {
    var alertCalls: Int = 0
    var lastAlertMessage: String? = null

    override fun alert(
        e: Exception,
        message: String,
    ) {
      alertCalls += 1
      lastAlertMessage = message
    }
  }

  private class CustomResponseExceptionHandler :
      BaseExceptionHandler(
          environment = MockEnvironment(),
      ) {
    override fun alert(
        e: Exception,
        message: String,
    ) {}

    override fun createErrorResponse(
        e: Exception,
        status: Int,
    ): BaseResponse<Any> =
        BaseResponse(
            code = "HOST_OVERRIDE_CODE",
            message = "Host override response",
        )
  }

  private class NoAlertExceptionHandler :
      BaseExceptionHandler(
          environment = MockEnvironment(),
      ) {
    var alertCalls: Int = 0

    override fun shouldAlert(
        e: Exception,
        request: HttpServletRequest,
        status: Int,
    ): Boolean = false

    override fun alert(
        e: Exception,
        message: String,
    ) {
      alertCalls += 1
    }
  }
}
