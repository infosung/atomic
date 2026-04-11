package com.infosung.atomic.event.log.ingest.api.adapter.`in`.web

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.event.log.ingest.api.application.exception.EventLogAsyncIngestQueueRejectedException
import com.infosung.atomic.spring.web.exception.BaseExceptionHandler
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Module-scoped exception advice for the official event-log ingest API. */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice(basePackages = ["com.infosung.atomic.event.log.ingest.api"])
class EventLogIngestHttpExceptionHandler(
    environment: Environment,
) : BaseExceptionHandler(environment) {
  override fun shouldAlert(
      e: Exception,
      request: HttpServletRequest,
      status: Int,
  ): Boolean = false

  override fun alert(
      e: Exception,
      message: String,
  ) {
    // Official event-log ingest advice intentionally does not emit alerts by default.
  }

  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun httpMessageNotReadableException(
      e: HttpMessageNotReadableException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    val httpException =
        HttpStatusException(
            status = 400,
            code = "MALFORMED_JSON_REQUEST",
            message = "Malformed JSON request body.",
            cause = e,
        )
    return httpStatusException(httpException, request)
  }

  @ExceptionHandler(EventLogAsyncIngestQueueRejectedException::class)
  fun asyncIngestQueueRejectedException(
      e: EventLogAsyncIngestQueueRejectedException,
      request: HttpServletRequest,
  ): ResponseEntity<BaseResponse<Any>> {
    val httpException =
        HttpStatusException(
            status = 503,
            code = "EVENT_LOG_INGEST_QUEUE_OVERFLOW",
            message = e.message,
            cause = e,
        )
    return ResponseEntity.status(503)
        .body(
            BaseResponse(
                code = "EVENT_LOG_INGEST_QUEUE_OVERFLOW",
                message = httpException.message,
            ),
        )
  }
}
