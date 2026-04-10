package com.infosung.atomic.event.log.parquet.application.port.out

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetObjectKeys
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetPartition
import java.io.Serializable
import java.time.Instant

/** One planned Parquet file backed by a contiguous spool subset. */
data class EventLogParquetFilePlan(
    val partition: EventLogParquetPartition,
    val objectKeys: EventLogParquetObjectKeys,
    val entries: List<EventLogSpoolEntry>,
) : Serializable {
  init {
    require(entries.isNotEmpty()) { "entries must not be empty." }
  }
}

/** Result of writing a staged Parquet file. */
data class EventLogStagedParquetFile(
    val partition: EventLogParquetPartition,
    val stagingObjectKey: String,
    val finalObjectKey: String,
    val recordCount: Long,
    val occurredAtMin: Instant,
    val occurredAtMax: Instant,
) : Serializable

/** Result of promoting a staged Parquet file into its final object key. */
data class EventLogPublishedParquetFile(
    val partition: EventLogParquetPartition,
    val objectKey: String,
    val recordCount: Long,
    val occurredAtMin: Instant,
    val occurredAtMax: Instant,
) : Serializable

/** Staging and promotion boundary for Parquet objects. */
interface EventLogParquetFileRepository {
  fun stage(
      plan: EventLogParquetFilePlan,
      records: List<EventLogRecord>,
  ): EventLogStagedParquetFile

  fun promote(staged: EventLogStagedParquetFile): EventLogPublishedParquetFile
}
