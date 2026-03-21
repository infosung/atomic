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
import org.springframework.web.bind.annotation.ExceptionHandler
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
