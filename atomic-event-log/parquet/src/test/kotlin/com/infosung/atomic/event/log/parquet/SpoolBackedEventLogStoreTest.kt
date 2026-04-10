package com.infosung.atomic.event.log.parquet

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.application.port.out.EventLogStoreAppendResult
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogValue
import com.infosung.atomic.event.log.parquet.adapter.out.spool.file.FileEventLogSpool
import com.infosung.atomic.event.log.parquet.adapter.out.spool.memory.InMemoryEventLogSpool
import com.infosung.atomic.event.log.parquet.adapter.out.store.SpoolBackedEventLogStore
import com.infosung.atomic.event.log.parquet.application.model.EventLogDeduplicationPolicy
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SpoolBackedEventLogStoreTest {
  @TempDir lateinit var tempDir: Path

  @Test
  fun `dedupe survives restart with file spool`() {
    val firstStore = SpoolBackedEventLogStore(spool = FileEventLogSpool(directory = tempDir))
    val first = firstStore.append(listOf(record("evt-1")))

    val secondStore = SpoolBackedEventLogStore(spool = FileEventLogSpool(directory = tempDir))
    val second = secondStore.append(listOf(record("evt-1")))

    assertEquals(listOf(EventLogStoreAppendResult.ACCEPTED), first)
    assertEquals(listOf(EventLogStoreAppendResult.DUPLICATE), second)
  }

  @Test
  fun `same batch duplicate is accepted only once`() {
    val store = SpoolBackedEventLogStore(spool = InMemoryEventLogSpool())

    val result = store.append(listOf(record("evt-1"), record("evt-1"), record("evt-2")))

    assertEquals(
        listOf(
            EventLogStoreAppendResult.ACCEPTED,
            EventLogStoreAppendResult.DUPLICATE,
            EventLogStoreAppendResult.ACCEPTED,
        ),
        result,
    )
  }

  @Test
  fun `committed keys outside retention lag are pruned on next append`() {
    val spool = InMemoryEventLogSpool()
    val store =
        SpoolBackedEventLogStore(
            spool = spool,
            deduplicationPolicy =
                EventLogDeduplicationPolicy(
                    retainedCommittedSequenceLag = 0,
                    maxTrackedKeys = 100,
                ),
        )

    assertEquals(listOf(EventLogStoreAppendResult.ACCEPTED), store.append(listOf(record("evt-1"))))
    spool.markCommittedThrough(1)

    val result = store.append(listOf(record("evt-1")))

    assertEquals(listOf(EventLogStoreAppendResult.ACCEPTED), result)
  }

  private fun record(eventId: String): EventLogRecord =
      EventLogRecord(
          schemaVersion = 1,
          serviceId = "totp",
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
