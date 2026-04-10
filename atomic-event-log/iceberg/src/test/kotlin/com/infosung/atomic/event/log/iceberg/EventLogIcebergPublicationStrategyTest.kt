package com.infosung.atomic.event.log.iceberg

import com.infosung.atomic.event.log.domain.EventLogPlatform
import com.infosung.atomic.event.log.iceberg.adapter.out.publication.HadoopCatalogEventLogPublicationStrategy
import com.infosung.atomic.event.log.iceberg.adapter.out.publication.RestCatalogEventLogPublicationStrategy
import com.infosung.atomic.event.log.iceberg.adapter.out.table.ServiceScopedEventLogIcebergTableStrategy
import com.infosung.atomic.event.log.iceberg.adapter.out.table.SharedEventLogIcebergTableStrategy
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergCommitRequest
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergCommitResult
import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergCommitStatus
import com.infosung.atomic.event.log.iceberg.application.port.out.EventLogIcebergCatalog
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportContext
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetPartition
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationMode
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationRequest
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublishedParquetFile
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EventLogIcebergPublicationStrategyTest {
  @Test
  fun `hadoop publication groups files by table and annotates snapshot properties`() {
    val catalog = RecordingIcebergCatalog()
    val strategy =
        HadoopCatalogEventLogPublicationStrategy(
            tableStrategy =
                ServiceScopedEventLogIcebergTableStrategy(namespace = listOf("lakehouse")),
            catalog = catalog,
            warehouseLocation = "s3://warehouse/event-log",
        )

    val receipt =
        strategy.publish(
            EventLogPublicationRequest(
                files =
                    listOf(file("totp", "totp-a.parquet"), file("fillingheart", "fh-a.parquet")),
                exportContext =
                    EventLogParquetExportContext(
                        serverId = "srv-1",
                        bootId = "boot-1",
                        flushSequence = 33,
                    ),
            ))

    assertEquals(EventLogPublicationMode.ICEBERG_HADOOP, receipt.mode)
    assertEquals(2, receipt.publishedFileCount)
    assertEquals("s3://warehouse/event-log", receipt.metadata["warehouseLocation"])
    assertEquals("2", receipt.metadata["appliedCommitCount"])
    assertEquals("0", receipt.metadata["replayedCommitCount"])
    assertEquals(2, catalog.requests.size)
    assertTrue(
        catalog.requests.all {
          it.snapshotProperties["event_log.publication_mode"] == "iceberg_hadoop"
        })
    assertTrue(catalog.requests.all { it.commitId.isNotBlank() })
  }

  @Test
  fun `rest publication keeps shared table and annotates endpoint`() {
    val catalog = RecordingIcebergCatalog()
    val strategy =
        RestCatalogEventLogPublicationStrategy(
            tableStrategy =
                SharedEventLogIcebergTableStrategy(
                    namespace = listOf("lakehouse"),
                    tableName = "event_logs",
                ),
            catalog = catalog,
            catalogEndpoint = "https://catalog.example.com",
        )

    val receipt =
        strategy.publish(
            EventLogPublicationRequest(
                files = listOf(file("totp", "totp-a.parquet"), file("totp", "totp-b.parquet")),
                exportContext =
                    EventLogParquetExportContext(
                        serverId = "srv-1",
                        bootId = "boot-1",
                        flushSequence = 34,
                    ),
            ))

    assertEquals(EventLogPublicationMode.ICEBERG_REST, receipt.mode)
    assertEquals("https://catalog.example.com", receipt.metadata["catalogEndpoint"])
    assertEquals("1", receipt.metadata["appliedCommitCount"])
    assertEquals("0", receipt.metadata["replayedCommitCount"])
    assertEquals(1, catalog.requests.size)
    assertEquals("lakehouse.event_logs", catalog.requests.single().tableId.qualifiedName())
    assertEquals(
        "iceberg_rest",
        catalog.requests.single().snapshotProperties["event_log.publication_mode"],
    )
  }

  @Test
  fun `retry after partial multi table success reuses commit id safely`() {
    val catalog = FailOnceIdempotentIcebergCatalog(failOnceTableName = "fillingheart_event_logs")
    val strategy =
        HadoopCatalogEventLogPublicationStrategy(
            tableStrategy =
                ServiceScopedEventLogIcebergTableStrategy(namespace = listOf("lakehouse")),
            catalog = catalog,
            warehouseLocation = "s3://warehouse/event-log",
        )
    val request =
        EventLogPublicationRequest(
            files =
                listOf(
                    file("totp", "totp-a.parquet"),
                    file("fillingheart", "fh-a.parquet"),
                ),
            exportContext =
                EventLogParquetExportContext(
                    serverId = "srv-2",
                    bootId = "boot-2",
                    flushSequence = 99,
                ),
        )

    assertFailsWith<IllegalStateException> { strategy.publish(request) }

    val receipt = strategy.publish(request)

    assertEquals(EventLogPublicationMode.ICEBERG_HADOOP, receipt.mode)
    assertEquals("1", receipt.metadata["appliedCommitCount"])
    assertEquals("1", receipt.metadata["replayedCommitCount"])
    assertEquals(3, catalog.attempts.size)
    assertEquals(
        listOf(
            EventLogIcebergCommitStatus.APPLIED,
            EventLogIcebergCommitStatus.ALREADY_COMMITTED,
            EventLogIcebergCommitStatus.APPLIED,
        ),
        catalog.attempts.map(Attempt::status),
    )
  }

  private fun file(serviceId: String, objectKey: String): EventLogPublishedParquetFile =
      EventLogPublishedParquetFile(
          partition =
              EventLogParquetPartition(
                  serviceId = serviceId,
                  platform = EventLogPlatform.API,
                  dt = LocalDate.parse("2026-04-10"),
                  hour = 10,
              ),
          objectKey = objectKey,
          recordCount = 1,
          occurredAtMin = Instant.parse("2026-04-10T10:00:00Z"),
          occurredAtMax = Instant.parse("2026-04-10T10:05:00Z"),
      )

  private class RecordingIcebergCatalog : EventLogIcebergCatalog {
    val requests = mutableListOf<EventLogIcebergCommitRequest>()

    override fun commit(request: EventLogIcebergCommitRequest): EventLogIcebergCommitResult {
      requests += request
      return EventLogIcebergCommitResult(
          commitId = request.commitId,
          status = EventLogIcebergCommitStatus.APPLIED,
          snapshotId = request.commitId,
          committedFileCount = request.dataFiles.size,
      )
    }
  }

  private class FailOnceIdempotentIcebergCatalog(
      private val failOnceTableName: String,
  ) : EventLogIcebergCatalog {
    val attempts = mutableListOf<Attempt>()
    private val committed = linkedMapOf<Pair<String, String>, EventLogIcebergCommitRequest>()
    private val failedCommitIds = mutableSetOf<String>()

    override fun commit(request: EventLogIcebergCommitRequest): EventLogIcebergCommitResult {
      val key = request.tableId.qualifiedName() to request.commitId
      val existing = committed[key]
      if (existing != null) {
        require(existing == request) { "same commitId must not point to a different request" }
        attempts +=
            Attempt(request = request, status = EventLogIcebergCommitStatus.ALREADY_COMMITTED)
        return EventLogIcebergCommitResult(
            commitId = request.commitId,
            status = EventLogIcebergCommitStatus.ALREADY_COMMITTED,
            snapshotId = request.commitId,
            committedFileCount = request.dataFiles.size,
        )
      }
      if (request.tableId.tableName == failOnceTableName && failedCommitIds.add(request.commitId)) {
        throw IllegalStateException("transient catalog failure")
      }
      committed[key] = request
      attempts += Attempt(request = request, status = EventLogIcebergCommitStatus.APPLIED)
      return EventLogIcebergCommitResult(
          commitId = request.commitId,
          status = EventLogIcebergCommitStatus.APPLIED,
          snapshotId = request.commitId,
          committedFileCount = request.dataFiles.size,
      )
    }
  }

  private data class Attempt(
      val request: EventLogIcebergCommitRequest,
      val status: EventLogIcebergCommitStatus,
  )
}
