package com.infosung.atomic.event.log.ingest.api.adapter.`in`.web

import com.infosung.atomic.event.log.application.exception.EventLogErrorCode
import com.infosung.atomic.event.log.application.exception.EventLogValidationException
import com.infosung.atomic.event.log.ingest.api.application.exception.EventLogAsyncIngestQueueRejectedException
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiResult
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.port.`in`.IngestEventLogApiUseCase
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class EventLogIngestControllerHttpContractTest {
  @Test
  fun `async ingest endpoint should return 202 enqueued response envelope`() {
    val useCase = RecordingIngestEventLogApiUseCase()
    useCase.nextResult =
        EventLogIngestApiResult.Enqueued(
            serviceId = "totp",
            schemaVersion = 1,
            receiptId = "receipt-1",
            queuedAt = Instant.parse("2026-04-10T00:00:01Z"),
            queuedEventCount = 1,
        )
    val controller =
        EventLogIngestController(useCase = useCase, requestMapper = EventLogIngestRequestMapper())
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/event-logs:batch")

    mockMvc
        .perform(
            post("/api/v1/event-logs:batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApiRequestJson()),
        )
        .andExpect(status().isAccepted)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("OK"))
        .andExpect(jsonPath("$.message").value("Success"))
        .andExpect(jsonPath("$.data.serviceId").value("totp"))
        .andExpect(jsonPath("$.data.schemaVersion").value(1))
        .andExpect(jsonPath("$.data.processingMode").value("ASYNC"))
        .andExpect(jsonPath("$.data.processingStatus").value("ENQUEUED"))
        .andExpect(jsonPath("$.data.receiptId").value("receipt-1"))
        .andExpect(jsonPath("$.data.queuedEventCount").value(1))

    assertEquals(1, useCase.invocationCount)
    assertNotNull(useCase.lastBatch)
    assertEquals("totp", useCase.lastBatch!!.serviceId)
    assertNotNull(useCase.lastMetadata)
    assertEquals("POST", useCase.lastMetadata!!.method)
  }

  @Test
  fun `configured endpoint path should be honored`() {
    val useCase = RecordingIngestEventLogApiUseCase()
    useCase.nextResult =
        EventLogIngestApiResult.Enqueued(
            serviceId = "totp",
            schemaVersion = 1,
            receiptId = "receipt-1",
            queuedAt = Instant.parse("2026-04-10T00:00:01Z"),
            queuedEventCount = 1,
        )
    val controller =
        EventLogIngestController(useCase = useCase, requestMapper = EventLogIngestRequestMapper())
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/internal/event-log/collect")

    mockMvc
        .perform(
            post("/internal/event-log/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApiRequestJson()),
        )
        .andExpect(status().isAccepted)
        .andExpect(jsonPath("$.code").value("OK"))
  }

  @Test
  fun `sync ingest endpoint should preserve duplicate and rejected summaries`() {
    val useCase = RecordingIngestEventLogApiUseCase()
    useCase.nextResult =
        EventLogIngestApiResult.Completed(
            serviceId = "totp",
            schemaVersion = 1,
            result =
                com.infosung.atomic.event.log.application.model.EventLogIngestResult(
                    accepted = 0,
                    duplicate = 1,
                    rejected = 1,
                    results =
                        listOf(
                            com.infosung.atomic.event.log.application.model
                                .EventLogEventIngestResult(
                                    eventId = "evt-1",
                                    status =
                                        com.infosung.atomic.event.log.application.model
                                            .EventLogStatus
                                            .DUPLICATE,
                                ),
                            com.infosung.atomic.event.log.application.model
                                .EventLogEventIngestResult(
                                    eventId = "evt-2",
                                    status =
                                        com.infosung.atomic.event.log.application.model
                                            .EventLogStatus
                                            .REJECTED,
                                    code = EventLogErrorCode.EVENT_LOG_EVENT_NAME_INVALID,
                                ),
                        ),
                ),
        )
    val controller =
        EventLogIngestController(useCase = useCase, requestMapper = EventLogIngestRequestMapper())
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/event-logs:batch")

    mockMvc
        .perform(
            post("/api/v1/event-logs:batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validDuplicateAndRejectedRequestJson()),
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.data.processingMode").value("SYNC"))
        .andExpect(jsonPath("$.data.processingStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.accepted").value(0))
        .andExpect(jsonPath("$.data.duplicate").value(1))
        .andExpect(jsonPath("$.data.rejected").value(1))
        .andExpect(jsonPath("$.data.results[0].eventId").value("evt-1"))
        .andExpect(jsonPath("$.data.results[0].status").value("DUPLICATE"))
        .andExpect(jsonPath("$.data.results[1].eventId").value("evt-2"))
        .andExpect(jsonPath("$.data.results[1].status").value("REJECTED"))
        .andExpect(jsonPath("$.data.results[1].code").value("EVENT_LOG_EVENT_NAME_INVALID"))
  }

  @Test
  fun `shallow request validation should still return documented 400 error envelope`() {
    val useCase = RecordingIngestEventLogApiUseCase()
    val controller =
        EventLogIngestController(useCase = useCase, requestMapper = EventLogIngestRequestMapper())
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/event-logs:batch")

    mockMvc
        .perform(
            post("/api/v1/event-logs:batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"schemaVersion":1,"serviceId":"","events":[]}"""),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("EVENT_LOG_REQUEST_INVALID"))
        .andExpect(jsonPath("$.message").value("serviceId must not be blank."))
  }

  @Test
  fun `sync validation errors from use case should return documented 400 error envelope`() {
    val useCase = RecordingIngestEventLogApiUseCase()
    useCase.failure =
        EventLogValidationException(
            code = EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID,
            message = "platformPayload.status must be a 32-bit integer.",
        )
    val controller =
        EventLogIngestController(useCase = useCase, requestMapper = EventLogIngestRequestMapper())
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/event-logs:batch")

    mockMvc
        .perform(
            post("/api/v1/event-logs:batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApiRequestJson()),
        )
        .andExpect(status().isBadRequest)
        .andExpect(jsonPath("$.code").value("EVENT_LOG_PLATFORM_PAYLOAD_INVALID"))
        .andExpect(jsonPath("$.message").value("platformPayload.status must be a 32-bit integer."))
  }

  @Test
  fun `async queue overflow should return documented 503 error envelope`() {
    val useCase = RecordingIngestEventLogApiUseCase()
    useCase.queueFailure =
        EventLogAsyncIngestQueueRejectedException(
            "Async ingest queue is saturated. Increase capacity or reduce request rate.",
        )
    val controller =
        EventLogIngestController(useCase = useCase, requestMapper = EventLogIngestRequestMapper())
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/event-logs:batch")

    mockMvc
        .perform(
            post("/api/v1/event-logs:batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validApiRequestJson()),
        )
        .andExpect(status().isServiceUnavailable)
        .andExpect(jsonPath("$.code").value("EVENT_LOG_INGEST_QUEUE_OVERFLOW"))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Async ingest queue is saturated. Increase capacity or reduce request rate."),
        )
  }

  @Test
  fun `malformed json should return documented 400 error envelope`() {
    val useCase = RecordingIngestEventLogApiUseCase()
    val controller =
        EventLogIngestController(useCase = useCase, requestMapper = EventLogIngestRequestMapper())
    val mockMvc = newMockMvc(controller = controller, endpointPath = "/api/v1/event-logs:batch")

    mockMvc
        .perform(
            post("/api/v1/event-logs:batch").contentType(MediaType.APPLICATION_JSON).content("{"),
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("MALFORMED_JSON_REQUEST"))
        .andExpect(jsonPath("$.message").value("Malformed JSON request body."))
  }

  private fun newMockMvc(
      controller: EventLogIngestController,
      endpointPath: String,
  ): MockMvc {
    return MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(EventLogIngestHttpExceptionHandler(MockEnvironment()))
        .addPlaceholderValue("atomic.event.log.ingest.endpoint-path", endpointPath)
        .build()
  }

  private fun validApiRequestJson(): String =
      """
        {
          "schemaVersion": 1,
          "serviceId": "totp",
          "events": [
            {
              "eventId": "evt-1",
              "eventName": "api.request",
              "eventType": "REQUEST",
              "occurredAt": "2026-04-10T00:00:00Z",
              "platform": "API",
              "actorId": "user-1",
              "traceId": "trace-1",
              "tags": ["public"],
              "platformPayload": {
                "httpMethod": "POST",
                "endpoint": "/api/v1/totp",
                "status": "oops",
                "executeTimeMs": 42
              },
              "businessPayload": {
                "step": "issue",
                "success": true,
                "elapsedMs": 123
              }
            }
          ]
        }
      """
          .trimIndent()

  private fun validDuplicateAndRejectedRequestJson(): String =
      """
        {
          "schemaVersion": 1,
          "serviceId": "totp",
          "events": [
            {
              "eventId": "evt-1",
              "eventName": "api.request",
              "occurredAt": "2026-04-10T00:00:00Z",
              "platform": "API",
              "platformPayload": {
                "httpMethod": "POST",
                "endpoint": "/api/v1/totp"
              }
            },
            {
              "eventId": "evt-2",
              "eventName": "api.invalid",
              "occurredAt": "2026-04-10T00:00:01Z",
              "platform": "API",
              "platformPayload": {
                "httpMethod": "GET",
                "endpoint": "/api/v1/totp"
              }
            }
          ]
        }
      """
          .trimIndent()

  private class RecordingIngestEventLogApiUseCase : IngestEventLogApiUseCase {
    var nextResult: EventLogIngestApiResult =
        EventLogIngestApiResult.Enqueued(
            serviceId = "totp",
            schemaVersion = 1,
            receiptId = "receipt-default",
            queuedAt = Instant.parse("2026-04-10T00:00:00Z"),
            queuedEventCount = 1,
        )
    var failure: EventLogValidationException? = null
    var queueFailure: EventLogAsyncIngestQueueRejectedException? = null
    var invocationCount: Int = 0
    var lastBatch: EventLogIngestIntakeBatch? = null
    var lastMetadata: EventLogIngestApiRequestMetadata? = null

    override fun ingest(
        batch: EventLogIngestIntakeBatch,
        requestMetadata: EventLogIngestApiRequestMetadata,
    ): EventLogIngestApiResult {
      invocationCount += 1
      lastBatch = batch
      lastMetadata = requestMetadata
      failure?.let { throw it }
      queueFailure?.let { throw it }
      return nextResult
    }
  }
}
