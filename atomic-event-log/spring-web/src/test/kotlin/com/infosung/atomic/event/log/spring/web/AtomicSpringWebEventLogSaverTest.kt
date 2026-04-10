package com.infosung.atomic.event.log.spring.web

import com.infosung.atomic.event.log.adapter.out.store.InMemoryEventLogStore
import com.infosung.atomic.event.log.application.service.EventLogIngestionService
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogValue
import com.infosung.atomic.event.log.spring.web.adapter.`in`.web.AtomicSpringWebEventLogSaver
import com.infosung.atomic.spring.web.log.ServiceApiRequestLog
import com.infosung.atomic.spring.web.log.ServiceApiResponseLog
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class AtomicSpringWebEventLogSaverTest {
  @Test
  fun `spring web logs are mapped into api event logs`() {
    val store = InMemoryEventLogStore()
    val saver =
        AtomicSpringWebEventLogSaver(
            serviceId = "totp",
            ingestionService = EventLogIngestionService(store = store),
        )

    saver.saveAll(
        listOf(
            ServiceApiRequestLog(
                traceId = "trace-1",
                logTime = 1_710_000_000_000,
                httpMethod = "POST",
                endPoint = "/v1/totp/verify",
                userId = "user-1",
                deviceId = "device-1",
                clientIp = "127.0.0.1",
                query = "{\"tenant\":\"totp\"}",
                body = "{\"password\":\"masked\"}",
            ),
            ServiceApiResponseLog(
                traceId = "trace-1",
                logTime = 1_710_000_000_100,
                httpMethod = "POST",
                executeTime = 100,
                status = 200,
                endPoint = "/v1/totp/verify",
                userId = "user-1",
                deviceId = "device-1",
                clientIp = "127.0.0.1",
            ),
        ))

    val records = store.snapshot().sortedBy { it.eventName }
    assertEquals(2, records.size)

    val request = records[0]
    assertEquals(EventLogPlatform.API, request.platform)
    assertEquals(EventLogEventType.REQUEST, request.eventType)
    assertEquals("api.request", request.eventName)
    assertEquals(
        "127.0.0.xxx",
        assertIs<EventLogValue.Text>(request.platformPayload.getValue("clientIpMasked")).value,
    )

    val response = records[1]
    assertEquals(EventLogEventType.RESPONSE, response.eventType)
    assertEquals(
        200,
        assertIs<EventLogValue.Integer>(response.platformPayload.getValue("status")).value,
    )
  }
}
