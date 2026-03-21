package com.infosung.atomic.spring.web.exception

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import jakarta.servlet.http.HttpServletRequest
import java.io.PrintWriter
import java.io.StringWriter
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Base Spring MVC exception handler.
 *
 * Converts exceptions into [BaseResponse] envelopes and delegates alert delivery through [alert].
 */
abstract class BaseExceptionHandler(
    environment: Environment,
) {
  private val log = LoggerFactory.getLogger(BaseExceptionHandler::class.java)
  private val env = environment.activeProfiles

  /** Handles uncaught exceptions as HTTP 500. */
  @ExceptionHandler(Exception::class)
  fun exception(
      e: Exception,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    val status = HttpStatus.INTERNAL_SERVER_ERROR.value()
    handle(e, request, status)
    return ResponseEntity.status(status).body(errorResponse(e = e, status = status))
  }

  /** Handles missing-resource exceptions as HTTP 404. */
  @ExceptionHandler(NoResourceFoundException::class)
  fun noResourceFoundException(
      e: NoResourceFoundException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    val status = HttpStatus.NOT_FOUND.value()
    handle(e, request, status)
    return ResponseEntity.status(status).body(errorResponse(e = e, status = status))
  }

  /** Handles [HttpStatusException] using its embedded status code. */
  @ExceptionHandler(HttpStatusException::class)
  fun httpStatusException(
      e: HttpStatusException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    handle(e, request, e.status)
    return ResponseEntity.status(e.status).body(errorResponse(e = e, status = e.status))
  }

  /** Handles missing request parameter exceptions as HTTP 400. */
  @ExceptionHandler(MissingServletRequestParameterException::class)
  fun missingServletRequestParameterException(
      e: MissingServletRequestParameterException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    return clientError(
        request = request,
        code = "MISSING_REQUEST_PARAMETER",
        message = "Required request parameter '${e.parameterName}' is missing.",
        cause = e,
    )
  }

  /** Handles missing multipart part exceptions as HTTP 400. */
  @ExceptionHandler(MissingServletRequestPartException::class)
  fun missingServletRequestPartException(
      e: MissingServletRequestPartException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    return clientError(
        request = request,
        code = "MISSING_REQUEST_PART",
        message = "Required request part '${e.requestPartName}' is missing.",
        cause = e,
    )
  }

  /** Handles request binding exceptions as HTTP 400. */
  @ExceptionHandler(ServletRequestBindingException::class)
  fun servletRequestBindingException(
      e: ServletRequestBindingException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    return clientError(
        request = request,
        code = "INVALID_REQUEST_BINDING",
        message = e.message ?: "Invalid request binding.",
        cause = e,
    )
  }

  /** Handles path variable resolution failures as HTTP 400. */
  @ExceptionHandler(MissingPathVariableException::class)
  fun missingPathVariableException(
      e: MissingPathVariableException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    return clientError(
        request = request,
        code = "MISSING_PATH_VARIABLE",
        message = "Required path variable '${e.variableName}' is missing.",
        cause = e,
    )
  }

  /** Handles request parameter type mismatch as HTTP 400. */
  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun methodArgumentTypeMismatchException(
      e: MethodArgumentTypeMismatchException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    return clientError(
        request = request,
        code = "INVALID_REQUEST_PARAMETER_TYPE",
        message = "Invalid value for request parameter '${e.name}'.",
        cause = e,
    )
  }

  /** Handles unsupported request methods as HTTP 405. */
  @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
  fun httpRequestMethodNotSupportedException(
      e: HttpRequestMethodNotSupportedException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    val status = HttpStatus.METHOD_NOT_ALLOWED.value()
    val httpException =
        HttpStatusException(
            status = status,
            message = e.message ?: "HTTP method is not supported.",
            cause = e,
            code = "HTTP_METHOD_NOT_SUPPORTED",
        )
    handle(httpException, request, status)
    return ResponseEntity.status(status).body(errorResponse(e = httpException, status = status))
  }

  /** Handles unsupported media types as HTTP 415. */
  @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
  fun httpMediaTypeNotSupportedException(
      e: HttpMediaTypeNotSupportedException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    val status = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()
    val httpException =
        HttpStatusException(
            status = status,
            message = e.message ?: "HTTP media type is not supported.",
            cause = e,
            code = "UNSUPPORTED_MEDIA_TYPE",
        )
    handle(httpException, request, status)
    return ResponseEntity.status(status).body(errorResponse(e = httpException, status = status))
  }

  private fun handle(
      e: Exception,
      request: HttpServletRequest,
      status: Int,
  ) {
    if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
      log.error(
          "Unhandled server exception: method={}, uri={}, status={}",
          request.method,
          request.requestURI,
          status,
          e,
      )
    } else {
      log.warn(
          "Handled client exception: method={}, uri={}, status={}, type={}",
          request.method,
          request.requestURI,
          status,
          e::class.simpleName,
      )
      log.trace("Client exception stacktrace", e)
    }
    alertMessage(e, request)
  }

  /** Sends alert message in non-prod profiles. */
  fun alertMessage(e: Exception, request: HttpServletRequest) {
    if (env.contains("prod")) {
      log.debug("Skipping alert in prod profile")
      return
    }
    val message = stackTraceWithRequestInfo(request, e)
    log.debug("Sending alert message for exception handling")
    alert(e, message)
  }

  /** Implement integration-specific alert delivery (for example Slack/Discord/email). */
  abstract fun alert(e: Exception, message: String)

  private fun errorResponse(
      e: Exception,
      status: Int,
  ): BaseResponse<Any> {
    if (status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
      return BaseResponse(
          code = resolveCode(e),
          message = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
      )
    }
    return BaseResponse.error(e = e)
  }

  private fun resolveCode(e: Exception): String {
    return (e as? HttpStatusException)?.code?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
  }

  private fun clientError(
      request: HttpServletRequest,
      code: String,
      message: String,
      cause: Exception,
  ): ResponseEntity<BaseResponse<Any>> {
    val status = HttpStatus.BAD_REQUEST.value()
    val httpException =
        HttpStatusException(
            status = status,
            message = message,
            cause = cause,
            code = code,
        )
    handle(httpException, request, status)
    return ResponseEntity.status(status).body(errorResponse(e = httpException, status = status))
  }

  private fun stackTraceWithRequestInfo(request: HttpServletRequest, e: Exception): String {
    val stringBuilder = kotlin.text.StringBuilder()
    stringBuilder
        .append("uri: ")
        .append(request.method)
        .append(' ')
        .append(request.requestURI)
        .append('\n')
        .append("<br />")

    val headerNames = request.headerNames
    while (headerNames.hasMoreElements()) {
      val key = headerNames.nextElement()
      if (key.equals(HttpHeaders.AUTHORIZATION, ignoreCase = true) ||
          key.equals(HttpHeaders.COOKIE, ignoreCase = true) ||
          key.equals(HttpHeaders.SET_COOKIE, ignoreCase = true))
          continue
      val value = request.getHeader(key)
      stringBuilder.append(key).append(": ").append(value).append('\n').append("<br />")
    }

    val printStackTrace = e.stackTrace()
    stringBuilder.append(printStackTrace)
    return stringBuilder.toString()
  }

  fun Exception.stackTrace(): String {
    val writer = StringWriter()
    this.printStackTrace(PrintWriter(writer))
    return writer.toString()
  }
}
