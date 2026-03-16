package com.infosung.atomic.app.oauth

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Controller-specific HTTP exception mapping for the app OAuth redirect API. */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [AppOauthRedirectController::class])
class AppOauthRedirectHttpExceptionHandler {
  private val log = LoggerFactory.getLogger(this::class.java)

  @ExceptionHandler(HttpStatusException::class)
  fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
    if (e.status >= 500) {
      log.error(
          "App oauth redirect API failed with HttpStatusException: status={}, message={}",
          e.status,
          e.message,
          e,
      )
    } else {
      log.warn(
          "App oauth redirect API rejected request: status={}, message={}",
          e.status,
          e.message,
      )
    }
    return ResponseEntity.status(e.status).body(BaseResponse.error(e))
  }
}
