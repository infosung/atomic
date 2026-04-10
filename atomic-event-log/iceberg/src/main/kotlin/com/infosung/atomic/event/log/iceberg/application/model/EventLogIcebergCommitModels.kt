package com.infosung.atomic.event.log.iceberg.application.model

import com.infosung.atomic.event.log.domain.EventLogPlatform
import java.time.Instant

/** One published Parquet file that is ready to be committed to an Iceberg table. */
data class EventLogIcebergDataFile(
    val objectKey: String,
    val serviceId: String,
    val platform: EventLogPlatform,
    val partitionValues: Map<String, String>,
    val recordCount: Long,
    val occurredAtMin: Instant,
    val occurredAtMax: Instant,
)

/** Batch commit request for one Iceberg table. */
data class EventLogIcebergCommitRequest(
    val tableId: EventLogIcebergTableId,
    val dataFiles: List<EventLogIcebergDataFile>,
    val commitId: String,
    val snapshotProperties: Map<String, String> = emptyMap(),
) {
  init {
    require(dataFiles.isNotEmpty()) { "dataFiles must not be empty." }
    require(commitId.isNotBlank()) { "commitId must not be blank." }
  }
}

/** Replay-safe status for one Iceberg commit attempt. */
enum class EventLogIcebergCommitStatus {
  APPLIED,
  ALREADY_COMMITTED,
}

/** Result of an Iceberg data-file commit. */
data class EventLogIcebergCommitResult(
    val commitId: String,
    val status: EventLogIcebergCommitStatus,
    val snapshotId: String? = null,
    val committedFileCount: Int,
) {
  init {
    require(commitId.isNotBlank()) { "commitId must not be blank." }
    require(committedFileCount >= 0) { "committedFileCount must be greater than or equal to zero." }
  }
}
