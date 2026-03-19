package com.infosung.atomic.app.version

import com.infosung.atomic.app.version.adapter.`in`.web.AppVersionHttpExceptionHandlerWebAdapter
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Controller-specific HTTP exception mapping for the app version API. */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [AppVersionController::class])
class AppVersionHttpExceptionHandler {
  private val webAdapter = AppVersionHttpExceptionHandlerWebAdapter()

  @ExceptionHandler(HttpStatusException::class)
  fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
    return webAdapter.httpStatusException(e)
  }
}
