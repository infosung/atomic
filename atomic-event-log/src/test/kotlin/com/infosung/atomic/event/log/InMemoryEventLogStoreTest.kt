package com.infosung.atomic.event.log

import com.infosung.atomic.event.log.adapter.out.store.InMemoryEventLogStore
import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.application.port.out.EventLogStoreAppendResult
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogValue
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class InMemoryEventLogStoreTest {
  @Test
  fun `same event id in same service is duplicate`() {
    val store = InMemoryEventLogStore()

    val first = store.append(listOf(record(serviceId = "totp", eventId = "evt-1")))
    val second = store.append(listOf(record(serviceId = "totp", eventId = "evt-1")))

    assertEquals(listOf(EventLogStoreAppendResult.ACCEPTED), first)
    assertEquals(listOf(EventLogStoreAppendResult.DUPLICATE), second)
  }

  @Test
  fun `same event id in different service is accepted`() {
    val store = InMemoryEventLogStore()

    val first = store.append(listOf(record(serviceId = "totp", eventId = "evt-1")))
    val second = store.append(listOf(record(serviceId = "fillingheart", eventId = "evt-1")))

    assertEquals(listOf(EventLogStoreAppendResult.ACCEPTED), first)
    assertEquals(listOf(EventLogStoreAppendResult.ACCEPTED), second)
  }

  private fun record(
      serviceId: String,
      eventId: String,
  ): EventLogRecord =
      EventLogRecord(
          schemaVersion = 1,
          serviceId = serviceId,
          eventId = eventId,
          eventName = "api.request",
          eventType = EventLogEventType.REQUEST,
          platform = EventLogPlatform.API,
          occurredAt = Instant.parse("2026-04-10T10:15:30Z"),
          receivedAt = Instant.parse("2026-04-10T10:15:31Z"),
          actorId = "user-1",
          traceId = "trace-1",
          tags = setOf("api"),
          platformPayload =
              mapOf(
                  "httpMethod" to EventLogValue.Text("GET"),
                  "endpoint" to EventLogValue.Text("/v1/totp"),
              ),
          businessPayload = emptyMap(),
      )
}
