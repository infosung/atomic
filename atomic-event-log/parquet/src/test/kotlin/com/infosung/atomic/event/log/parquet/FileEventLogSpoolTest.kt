package com.infosung.atomic.event.log.parquet

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogValue
import com.infosung.atomic.event.log.parquet.adapter.out.spool.file.FileEventLogSpool
import com.infosung.atomic.event.log.parquet.application.model.EventLogSpoolSyncMode
import com.infosung.atomic.event.log.parquet.application.model.EventLogSpoolWritePolicy
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolEntry
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FileEventLogSpoolTest {
  @TempDir lateinit var tempDir: Path

  @Test
  fun `checkpoint and pending entries survive restart`() {
    val spool = FileEventLogSpool(directory = tempDir)
    spool.append(listOf(record("evt-1"), record("evt-2")))
    spool.markCommittedThrough(1)

    val restarted = FileEventLogSpool(directory = tempDir)

    assertEquals(1L, restarted.checkpoint().committedSequence)
    assertEquals(listOf(2L), restarted.pending().map(EventLogSpoolEntry::sequence))
  }

  @Test
  fun `restart ignores truncated tail record and keeps valid entries`() {
    val spool = FileEventLogSpool(directory = tempDir)
    spool.append(listOf(record("evt-1"), record("evt-2")))
    Files.write(
        tempDir.resolve("event-log-spool.bin"),
        ByteBuffer.allocate(Int.SIZE_BYTES).putInt(64).array() + byteArrayOf(1, 2, 3),
        StandardOpenOption.APPEND,
    )

    val restarted = FileEventLogSpool(directory = tempDir)

    assertEquals(0L, restarted.checkpoint().committedSequence)
    assertEquals(listOf(1L, 2L), restarted.pending().map(EventLogSpoolEntry::sequence))
  }

  @Test
  fun `checkpoint sync mode keeps committed and pending entries across restart`() {
    val spool =
        FileEventLogSpool(
            directory = tempDir,
            writePolicy =
                EventLogSpoolWritePolicy(syncMode = EventLogSpoolSyncMode.SYNC_ON_CHECKPOINT),
        )
    spool.append(listOf(record("evt-1"), record("evt-2")))
    spool.markCommittedThrough(1)

    val restarted =
        FileEventLogSpool(
            directory = tempDir,
            writePolicy =
                EventLogSpoolWritePolicy(syncMode = EventLogSpoolSyncMode.SYNC_ON_CHECKPOINT),
        )

    assertEquals(1L, restarted.checkpoint().committedSequence)
    assertEquals(listOf(2L), restarted.pending().map(EventLogSpoolEntry::sequence))
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
