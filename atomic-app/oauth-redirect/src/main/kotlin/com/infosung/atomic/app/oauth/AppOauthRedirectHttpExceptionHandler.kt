package com.infosung.atomic.app.oauth

import com.infosung.atomic.app.oauth.adapter.`in`.web.OauthRedirectHttpExceptionHandlerWebAdapter
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Controller-specific HTTP exception mapping for the app OAuth redirect API. */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [AppOauthRedirectController::class])
class AppOauthRedirectHttpExceptionHandler {
  private val webAdapter = OauthRedirectHttpExceptionHandlerWebAdapter()

  @ExceptionHandler(HttpStatusException::class)
  fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
    return webAdapter.handleHttpStatusException(e)
  }
}
