package com.infosung.atomic.event.log.ingest.api.adapter.`in`.web

import com.infosung.atomic.contract.exception.HttpStatusException
import com.infosung.atomic.contract.response.BaseResponse
import com.infosung.atomic.event.log.application.exception.EventLogErrorCode
import com.infosung.atomic.event.log.application.exception.EventLogValidationException
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiResult
import com.infosung.atomic.event.log.ingest.api.application.port.`in`.IngestEventLogApiUseCase
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Official Spring MVC controller for the shared event-log batch ingest API. */
@RestController
class EventLogIngestController(
    private val useCase: IngestEventLogApiUseCase,
    private val requestMapper: EventLogIngestRequestMapper,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  @PostMapping("\${atomic.event.log.ingest.endpoint-path:/api/v1/event-logs:batch}")
  fun ingest(
      @RequestBody request: EventLogBatchIngestRequestDto,
      servletRequest: HttpServletRequest,
  ): ResponseEntity<BaseResponse<EventLogBatchIngestResponseDto>> {
    val batch =
        try {
          requestMapper.toIntakeBatch(request)
        } catch (e: EventLogValidationException) {
          throw e.toHttpStatusException()
        }
    log.debug(
        "Event log ingest HTTP request accepted: serviceId={}, eventCount={}, uri={}",
        batch.serviceId,
        batch.events.size,
        servletRequest.requestURI,
    )
    val requestMetadata = servletRequest.toRequestMetadata()
    val result =
        try {
          useCase.ingest(batch = batch, requestMetadata = requestMetadata)
        } catch (e: EventLogValidationException) {
          log.warn(
              "Event log ingest HTTP request rejected: serviceId={}, code={}, message={}",
              batch.serviceId,
              e.code,
              e.message,
          )
          throw e.toHttpStatusException()
        }
    val response = BaseResponse.ok(requestMapper.toResponse(result = result))
    log.debug(
        "Event log ingest HTTP request completed: serviceId={}, mode={}, processingStatus={}",
        batch.serviceId,
        result.mode,
        result.processingStatus,
    )
    return when (result) {
      is EventLogIngestApiResult.Enqueued ->
          ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
      is EventLogIngestApiResult.Completed -> ResponseEntity.ok(response)
    }
  }

  private fun HttpServletRequest.toRequestMetadata(): EventLogIngestApiRequestMetadata {
    val headers = linkedMapOf<String, String>()
    val names = headerNames
    while (names.hasMoreElements()) {
      val headerName = names.nextElement()
      headers[headerName.lowercase()] = getHeader(headerName)
    }
    return EventLogIngestApiRequestMetadata(
        method = method,
        requestUri = requestURI,
        clientIp = remoteAddr,
        userAgent = getHeader("User-Agent"),
        headers = headers,
    )
  }

  private fun EventLogValidationException.toHttpStatusException(): HttpStatusException {
    val status =
        when (code) {
          EventLogErrorCode.EVENT_LOG_BATCH_TOO_LARGE -> 413
          else -> 400
        }
    return HttpStatusException(
        status = status,
        code = code.name,
        message = message,
        cause = this,
    )
  }
}
