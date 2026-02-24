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

abstract class BaseExceptionHandler(
    environment: Environment,
) {
  private val log = LoggerFactory.getLogger(BaseExceptionHandler::class.java)
  private val env = environment.activeProfiles

  @ExceptionHandler(Exception::class)
  fun exception(
      e: Exception,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    val status = HttpStatus.INTERNAL_SERVER_ERROR.value()
    handle(e, request, status)
    return ResponseEntity.status(status).body(BaseResponse.error(e = e))
  }

  @ExceptionHandler(NoResourceFoundException::class)
  fun noResourceFoundException(
      e: NoResourceFoundException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    val status = HttpStatus.NOT_FOUND.value()
    handle(e, request, status)
    return ResponseEntity.status(status).body(BaseResponse.error(e = e))
  }

  @ExceptionHandler(HttpStatusException::class)
  fun httpStatusException(
      e: HttpStatusException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    handle(e, request, e.status)
    return ResponseEntity.status(e.status).body(BaseResponse.error(e = e))
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

  fun alertMessage(e: Exception, request: HttpServletRequest) {
    if (env.contains("prod")) {
      log.debug("Skipping alert in prod profile")
      return
    }
    val message = stackTraceWithRequestInfo(request, e)
    log.debug("Sending alert message for exception handling")
    alert(e, message)
  }

  abstract fun alert(e: Exception, message: String)

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
