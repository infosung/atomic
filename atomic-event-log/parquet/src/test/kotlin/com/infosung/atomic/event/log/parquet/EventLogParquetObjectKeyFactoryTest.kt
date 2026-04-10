package com.infosung.atomic.event.log.parquet

import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportContext
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetPartition
import com.infosung.atomic.event.log.parquet.application.service.EventLogParquetObjectKeyFactory
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class EventLogParquetObjectKeyFactoryTest {
  @Test
  fun `object key contains service and platform partitions`() {
    val keys =
        EventLogParquetObjectKeyFactory(
                rootPrefix = "event-log", stagingPrefix = "event-log-staging")
            .create(
                partition =
                    EventLogParquetPartition(
                        serviceId = "totp",
                        platform = EventLogPlatform.API,
                        dt = LocalDate.parse("2026-04-10"),
                        hour = 9,
                    ),
                context =
                    EventLogParquetExportContext(
                        serverId = "srv-1",
                        bootId = "boot-1",
                        flushSequence = 42,
                    ),
            )

    assertEquals(
        "event-log-staging/server_id=srv-1/boot_id=boot-1/flush_seq=42.parquet.tmp",
        keys.stagingObjectKey,
    )
    assertEquals(
        "event-log/service_id=totp/platform=api/dt=2026-04-10/hour=09/server_id=srv-1/boot_id=boot-1/flush_seq=42.parquet",
        keys.finalObjectKey,
    )
  }
}
