package com.infosung.atomic.app.oauth.adapter.`in`.web

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity

internal class OauthRedirectHttpExceptionHandlerWebAdapter {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun handleHttpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
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
