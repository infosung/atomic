package com.infosung.atomic.app.storage.adapter.`in`.web

import com.infosung.atomic.app.storage.application.exception.ImageNotFoundException
import com.infosung.atomic.app.storage.application.exception.ImageOwnershipMismatchException
import com.infosung.atomic.app.storage.application.exception.InvalidImageRequestException
import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Controller-specific HTTP exception mapping for the app image API. */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = [AppStorageController::class])
class AppStorageHttpExceptionHandler {
  private val log = LoggerFactory.getLogger(this::class.java)

  @ExceptionHandler(InvalidImageRequestException::class)
  fun invalidImageRequestException(
      e: InvalidImageRequestException,
  ): ResponseEntity<BaseResponse<Any>> = errorResponse(400, requireNotNull(e.message), e)

  @ExceptionHandler(ImageNotFoundException::class)
  fun imageNotFoundException(
      e: ImageNotFoundException,
  ): ResponseEntity<BaseResponse<Any>> = errorResponse(404, requireNotNull(e.message), e)

  @ExceptionHandler(ImageOwnershipMismatchException::class)
  fun imageOwnershipMismatchException(
      e: ImageOwnershipMismatchException,
  ): ResponseEntity<BaseResponse<Any>> = errorResponse(403, requireNotNull(e.message), e)

  @ExceptionHandler(HttpStatusException::class)
  fun httpStatusException(e: HttpStatusException): ResponseEntity<BaseResponse<Any>> {
    return errorResponse(e.status, e.message, e)
  }

  private fun errorResponse(
      status: Int,
      message: String,
      cause: Throwable,
  ): ResponseEntity<BaseResponse<Any>> {
    if (status >= 500) {
      log.error(
          "App image API failed: status={}, message={}",
          status,
          message,
          cause,
      )
    } else {
      log.warn(
          "App image API rejected request: status={}, message={}",
          status,
          message,
      )
    }
    return ResponseEntity.status(status)
        .body(
            BaseResponse.error(
                HttpStatusException(
                    status = status,
                    message = message,
                    cause = cause,
                ),
            ),
        )
  }
}
