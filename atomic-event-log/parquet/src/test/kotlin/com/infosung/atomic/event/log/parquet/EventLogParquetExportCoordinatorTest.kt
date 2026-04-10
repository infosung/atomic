package com.infosung.atomic.event.log.parquet

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.domain.EventLogEventType
import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.domain.EventLogValue
import com.infosung.atomic.event.log.parquet.adapter.out.spool.memory.InMemoryEventLogSpool
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportContext
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportPolicy
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogParquetFilePlan
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogParquetFileRepository
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationMode
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationReceipt
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationRequest
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationStrategy
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublishedParquetFile
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolEntry
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogStagedParquetFile
import com.infosung.atomic.event.log.parquet.application.service.EventLogParquetExportCoordinator
import com.infosung.atomic.event.log.parquet.application.service.EventLogParquetObjectKeyFactory
import com.infosung.atomic.event.log.parquet.application.service.EventLogParquetPartitioner
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class EventLogParquetExportCoordinatorTest {
  @Test
  fun `export splits oversized partition and advances checkpoint after parquet-only publication`() {
    val spool = InMemoryEventLogSpool()
    spool.append(listOf(record("evt-1"), record("evt-2")))
    val repository = RecordingParquetRepository()
    val coordinator =
        EventLogParquetExportCoordinator(
            spool = spool,
            partitioner = EventLogParquetPartitioner(),
            keyFactory = EventLogParquetObjectKeyFactory(),
            repository = repository,
            exportPolicy = EventLogParquetExportPolicy(maxPendingDrain = 10, maxRecordsPerFile = 1),
        )

    val result =
        coordinator.export(
            EventLogParquetExportContext(
                serverId = "srv-1",
                bootId = "boot-1",
                flushSequence = 1,
            ))

    assertEquals(2, result.exportedFileCount)
    assertEquals(2, result.exportedRecordCount)
    assertEquals(2, result.committedThroughSequence)
    assertEquals(EventLogPublicationMode.PARQUET_ONLY, result.publicationMode)
    assertEquals(2, repository.promotedFiles.size)
    assertEquals(2L, spool.checkpoint().committedSequence)
  }

  @Test
  fun `checkpoint does not move when publication fails`() {
    val spool = InMemoryEventLogSpool()
    spool.append(listOf(record("evt-1")))
    val coordinator =
        EventLogParquetExportCoordinator(
            spool = spool,
            partitioner = EventLogParquetPartitioner(),
            keyFactory = EventLogParquetObjectKeyFactory(),
            repository = RecordingParquetRepository(),
            publicationStrategy = FailingPublicationStrategy(),
            exportPolicy =
                EventLogParquetExportPolicy(maxPendingDrain = 10, maxRecordsPerFile = 100),
        )

    assertFailsWith<IllegalStateException> {
      coordinator.export(
          EventLogParquetExportContext(
              serverId = "srv-1",
              bootId = "boot-1",
              flushSequence = 1,
          ))
    }
    assertEquals(0L, spool.checkpoint().committedSequence)
  }

  @Test
  fun `coordinator forwards promoted files to custom publication strategy`() {
    val spool = InMemoryEventLogSpool()
    spool.append(listOf(record("evt-1")))
    val repository = RecordingParquetRepository()
    val publicationStrategy = RecordingPublicationStrategy()
    val coordinator =
        EventLogParquetExportCoordinator(
            spool = spool,
            partitioner = EventLogParquetPartitioner(),
            keyFactory = EventLogParquetObjectKeyFactory(),
            repository = repository,
            publicationStrategy = publicationStrategy,
        )
    val context =
        EventLogParquetExportContext(
            serverId = "srv-1",
            bootId = "boot-1",
            flushSequence = 77,
        )

    val result = coordinator.export(context)

    assertEquals(EventLogPublicationMode.ICEBERG_REST, result.publicationMode)
    assertEquals("catalog.example.com", result.publicationMetadata["catalogEndpoint"])
    assertEquals(1, publicationStrategy.requests.size)
    assertEquals(repository.promotedFiles, publicationStrategy.requests.single().files)
    assertSame(context, publicationStrategy.requests.single().exportContext)
  }

  @Test
  fun `export splits oversized partition by estimated bytes`() {
    val spool = InMemoryEventLogSpool()
    spool.append(
        listOf(
            record(eventId = "evt-1", bodySize = 200),
            record(eventId = "evt-2", bodySize = 200),
        ))
    val repository = RecordingParquetRepository()
    val coordinator =
        EventLogParquetExportCoordinator(
            spool = spool,
            partitioner = EventLogParquetPartitioner(),
            keyFactory = EventLogParquetObjectKeyFactory(),
            repository = repository,
            exportPolicy =
                EventLogParquetExportPolicy(
                    maxPendingDrain = 10,
                    maxRecordsPerFile = 10,
                    maxEstimatedBytesPerFile = 250,
                ),
        )

    val result =
        coordinator.export(
            EventLogParquetExportContext(
                serverId = "srv-1",
                bootId = "boot-1",
                flushSequence = 2,
            ))

    assertEquals(2, result.exportedFileCount)
    assertEquals(2, repository.promotedFiles.size)
    assertEquals(2L, spool.checkpoint().committedSequence)
  }

  @Test
  fun `export stops when flush duration budget is exhausted and commits published range only`() {
    val spool = InMemoryEventLogSpool()
    spool.append(listOf(record("evt-1"), record("evt-2"), record("evt-3")))
    val nanoClock = MutableNanoClock()
    val repository = TimeAdvancingParquetRepository(nanoClock = nanoClock, advanceNanos = 2)
    val coordinator =
        EventLogParquetExportCoordinator(
            spool = spool,
            partitioner = EventLogParquetPartitioner(),
            keyFactory = EventLogParquetObjectKeyFactory(),
            repository = repository,
            exportPolicy =
                EventLogParquetExportPolicy(
                    maxPendingDrain = 10,
                    maxRecordsPerFile = 1,
                    maxFlushDuration = Duration.ofNanos(1),
                ),
            nanoTimeSource = nanoClock::read,
        )

    val result =
        coordinator.export(
            EventLogParquetExportContext(
                serverId = "srv-1",
                bootId = "boot-1",
                flushSequence = 3,
            ))

    assertEquals(1, result.exportedFileCount)
    assertEquals(1, result.exportedRecordCount)
    assertEquals(1L, result.committedThroughSequence)
    assertEquals(1L, spool.checkpoint().committedSequence)
    assertEquals(listOf(2L, 3L), spool.pending().map(EventLogSpoolEntry::sequence))
  }

  private fun record(
      eventId: String,
      bodySize: Int = 0,
  ): EventLogRecord =
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
                  "body" to EventLogValue.Text("x".repeat(bodySize)),
              ),
          businessPayload = emptyMap(),
      )

  private open class RecordingParquetRepository : EventLogParquetFileRepository {
    val promotedFiles = mutableListOf<EventLogPublishedParquetFile>()

    override fun stage(
        plan: EventLogParquetFilePlan,
        records: List<EventLogRecord>,
    ): EventLogStagedParquetFile =
        EventLogStagedParquetFile(
            partition = plan.partition,
            stagingObjectKey = plan.objectKeys.stagingObjectKey,
            finalObjectKey = plan.objectKeys.finalObjectKey,
            recordCount = records.size.toLong(),
            occurredAtMin = records.minOf(EventLogRecord::occurredAt),
            occurredAtMax = records.maxOf(EventLogRecord::occurredAt),
        )

    override open fun promote(staged: EventLogStagedParquetFile): EventLogPublishedParquetFile {
      val published =
          EventLogPublishedParquetFile(
              partition = staged.partition,
              objectKey = staged.finalObjectKey,
              recordCount = staged.recordCount,
              occurredAtMin = staged.occurredAtMin,
              occurredAtMax = staged.occurredAtMax,
          )
      promotedFiles += published
      return published
    }
  }

  private class RecordingPublicationStrategy : EventLogPublicationStrategy {
    override val mode: EventLogPublicationMode = EventLogPublicationMode.ICEBERG_REST

    val requests = mutableListOf<EventLogPublicationRequest>()

    override fun publish(request: EventLogPublicationRequest): EventLogPublicationReceipt {
      requests += request
      return EventLogPublicationReceipt(
          mode = mode,
          publishedFileCount = request.files.size,
          metadata = mapOf("catalogEndpoint" to "catalog.example.com"),
      )
    }
  }

  private class FailingPublicationStrategy : EventLogPublicationStrategy {
    override val mode: EventLogPublicationMode = EventLogPublicationMode.ICEBERG_HADOOP

    override fun publish(request: EventLogPublicationRequest): EventLogPublicationReceipt {
      throw IllegalStateException("publication failed")
    }
  }

  private class MutableNanoClock(
      var now: Long = 0,
  ) {
    fun read(): Long = now
  }

  private class TimeAdvancingParquetRepository(
      private val nanoClock: MutableNanoClock,
      private val advanceNanos: Long,
  ) : RecordingParquetRepository() {
    override fun promote(staged: EventLogStagedParquetFile): EventLogPublishedParquetFile {
      nanoClock.now += advanceNanos
      return super.promote(staged)
    }
  }
}
