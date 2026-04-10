package com.infosung.atomic.event.log

import com.infosung.atomic.event.log.adapter.out.store.InMemoryEventLogStore
import com.infosung.atomic.event.log.application.exception.EventLogErrorCode
import com.infosung.atomic.event.log.application.exception.EventLogValidationException
import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.application.model.EventLogEvent
import com.infosung.atomic.event.log.application.model.EventLogStatus
import com.infosung.atomic.event.log.application.service.EventLogIngestionService
import com.infosung.atomic.event.log.domain.ApiEventLogPayload
import com.infosung.atomic.event.log.domain.ClientEventLogPayload
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogPlatformPayload
import com.infosung.atomic.event.log.domain.EventLogPolicy
import com.infosung.atomic.event.log.domain.EventLogValue
import com.infosung.atomic.event.log.domain.ServerEventLogPayload
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class EventLogIngestionServiceTest {
  @Test
  fun `invalid batch envelope is rejected`() {
    val service = EventLogIngestionService(store = InMemoryEventLogStore())

    val exception =
        assertFailsWith<EventLogValidationException> {
          service.ingest(
              EventLogBatch(
                  serviceId = "",
                  events = listOf(apiEvent(eventId = "evt-1")),
              ))
        }

    assertEquals(EventLogErrorCode.EVENT_LOG_REQUEST_INVALID, exception.code)
  }

  @Test
  fun `mixed batch returns accepted duplicate rejected and masks business payload`() {
    val store = InMemoryEventLogStore()
    val service = EventLogIngestionService(store = store)

    service.ingest(EventLogBatch(serviceId = "totp", events = listOf(apiEvent(eventId = "dup"))))

    val result =
        service.ingest(
            EventLogBatch(
                serviceId = "totp",
                events =
                    listOf(
                        apiEvent(eventId = "dup"),
                        apiEvent(eventId = "bad", eventName = ""),
                        serverEvent(
                            eventId = "accepted",
                            businessPayload =
                                mapOf(
                                    "accessToken" to EventLogValue.Text("secret"),
                                    "result" to EventLogValue.Text("ok"),
                                ),
                        ),
                    ),
            ))

    assertEquals(1, result.accepted)
    assertEquals(1, result.duplicate)
    assertEquals(1, result.rejected)
    assertEquals(EventLogStatus.DUPLICATE, result.results[0].status)
    assertEquals(EventLogStatus.REJECTED, result.results[1].status)
    assertEquals(EventLogStatus.ACCEPTED, result.results[2].status)

    val accepted = store.snapshot().single { it.eventId == "accepted" }
    val masked = assertIs<EventLogValue.Text>(accepted.businessPayload.getValue("accessToken"))
    assertEquals("***", masked.value)
    assertEquals(
        "ok",
        assertIs<EventLogValue.Text>(accepted.businessPayload.getValue("result")).value,
    )
  }

  @Test
  fun `client payload must use client platform`() {
    val service = EventLogIngestionService(store = InMemoryEventLogStore())

    val result =
        service.ingest(
            EventLogBatch(
                serviceId = "fillingheart",
                events =
                    listOf(
                        EventLogEvent(
                            eventId = "evt-1",
                            eventName = "screen.view",
                            occurredAt = Instant.parse("2026-04-10T10:15:30Z"),
                            platformPayload =
                                ClientEventLogPayload(
                                    platform = EventLogPlatform.API,
                                    appId = "windows-desktop",
                                    appVersion = "1.0.0",
                                    userPseudoId = "pseudo-1",
                                    sessionId = 1L,
                                ),
                        ),
                    ),
            ))

    assertEquals(0, result.accepted)
    assertEquals(0, result.duplicate)
    assertEquals(1, result.rejected)
    assertEquals(EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID, result.results.single().code)
  }

  @Test
  fun `platform payload toFields is evaluated once per accepted event`() {
    val store = InMemoryEventLogStore()
    val service = EventLogIngestionService(store = store)
    val payload = CountingPlatformPayload()

    val result =
        service.ingest(
            EventLogBatch(
                serviceId = "totp",
                events =
                    listOf(
                        EventLogEvent(
                            eventId = "evt-1",
                            eventName = "server.metric",
                            occurredAt = Instant.parse("2026-04-10T10:15:30Z"),
                            platformPayload = payload,
                        ),
                    ),
            ))

    assertEquals(1, result.accepted)
    assertEquals(1, payload.toFieldsInvocationCount)
    assertSame(payload.fields, store.snapshot().single().platformPayload)
  }

  private fun apiEvent(
      eventId: String,
      eventName: String = "api.request",
  ): EventLogEvent =
      EventLogEvent(
          eventId = eventId,
          eventName = eventName,
          occurredAt = Instant.parse("2026-04-10T10:15:30Z"),
          eventType = EventLogEventType.REQUEST,
          actorId = "user-1",
          traceId = "trace-1",
          platformPayload =
              ApiEventLogPayload(
                  httpMethod = "GET",
                  endpoint = "/v1/totp",
                  status = 200,
                  executeTimeMs = 15,
                  clientIpMasked = "127.0.0.xxx",
              ),
      )

  private fun serverEvent(
      eventId: String,
      businessPayload: Map<String, EventLogValue>,
  ): EventLogEvent =
      EventLogEvent(
          eventId = eventId,
          eventName = "server.error",
          occurredAt = Instant.parse("2026-04-10T10:15:30Z"),
          eventType = EventLogEventType.ERROR,
          traceId = "trace-2",
          platformPayload =
              ServerEventLogPayload(
                  hostName = "host-a",
                  instanceId = "instance-a",
                  loggerName = "Application",
                  level = "ERROR",
                  message = "boom",
              ),
          businessPayload = businessPayload,
      )

  private class CountingPlatformPayload : EventLogPlatformPayload {
    override val platform: EventLogPlatform = EventLogPlatform.SERVER
    val fields: Map<String, EventLogValue> =
        mapOf(
            "hostName" to EventLogValue.Text("host-a"),
            "instanceId" to EventLogValue.Text("instance-a"),
        )
    var toFieldsInvocationCount: Int = 0
      private set

    override fun toFields(): Map<String, EventLogValue> {
      toFieldsInvocationCount += 1
      return fields
    }

    override fun validate(policy: EventLogPolicy): String? = null
  }
}
