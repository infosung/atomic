package com.infosung.atomic.event.log.parquet

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogValue
import com.infosung.atomic.event.log.parquet.application.service.EventLogParquetPartitioner
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class EventLogParquetPartitionerTest {
  @Test
  fun `partition includes service platform date and hour`() {
    val partition =
        EventLogParquetPartitioner()
            .partition(
                EventLogRecord(
                    schemaVersion = 1,
                    serviceId = "fillingheart",
                    eventId = "evt-1",
                    eventName = "screen.view",
                    eventType = EventLogEventType.ACTION,
                    platform = EventLogPlatform.CLIENT_DESKTOP,
                    occurredAt = Instant.parse("2026-04-10T13:45:30Z"),
                    receivedAt = Instant.parse("2026-04-10T13:45:35Z"),
                    actorId = "user-1",
                    traceId = "trace-1",
                    tags = setOf("client"),
                    platformPayload =
                        mapOf(
                            "appId" to EventLogValue.Text("windows-desktop"),
                            "screen" to EventLogValue.Text("home"),
                        ),
                    businessPayload = emptyMap(),
                ))

    assertEquals("fillingheart", partition.serviceId)
    assertEquals(EventLogPlatform.CLIENT_DESKTOP, partition.platform)
    assertEquals(13, partition.hour)
    assertEquals("2026-04-10", partition.dt.toString())
  }
}
