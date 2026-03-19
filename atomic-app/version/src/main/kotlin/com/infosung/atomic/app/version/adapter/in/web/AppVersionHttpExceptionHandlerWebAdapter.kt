package com.infosung.atomic.app.version.adapter.`in`.web

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity

/** Internal web adapter for app-version HTTP exception mapping. */
internal class AppVersionHttpExceptionHandlerWebAdapter {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
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
