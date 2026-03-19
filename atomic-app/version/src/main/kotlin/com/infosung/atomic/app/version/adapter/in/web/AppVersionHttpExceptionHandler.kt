package com.infosung.atomic.app.version.adapter.`in`.web

import com.infosung.atomic.app.version.application.exception.InvalidAppVersionException
import com.infosung.atomic.app.version.application.exception.VersionPolicyNotFoundException
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** HTTP exception mapping for the app version web adapter. */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [AppVersionController::class])
class AppVersionHttpExceptionHandler {
  private val log = LoggerFactory.getLogger(this::class.java)

  @ExceptionHandler(InvalidAppVersionException::class)
  internal fun invalidAppVersion(e: InvalidAppVersionException): ResponseEntity<BaseResponse<Any>> {
    return httpStatusException(
        HttpStatusException(
            status = 400,
            message = e.message ?: "Invalid app version.",
            cause = e,
        ),
    )
  }

  @ExceptionHandler(VersionPolicyNotFoundException::class)
  internal fun versionPolicyNotFound(
      e: VersionPolicyNotFoundException,
  ): ResponseEntity<BaseResponse<Any>> {
    return httpStatusException(
        HttpStatusException(
            status = 404,
            message = e.message ?: "No version policy found.",
            cause = e,
        ),
    )
  }

  @ExceptionHandler(HttpStatusException::class)
  internal fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
    if (e.status >= 500) {
      log.error(
          "App version API failed with HttpStatusException: status={}, message={}",
          e.status,
          e.message,
          e,
      )
    } else {
      log.warn(
          "App version API rejected request: status={}, message={}",
          e.status,
          e.message,
      )
    }
    return ResponseEntity.status(e.status).body(BaseResponse.error(e))
  }
}
