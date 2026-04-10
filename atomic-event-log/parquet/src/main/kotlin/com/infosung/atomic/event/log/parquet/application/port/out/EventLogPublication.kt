package com.infosung.atomic.event.log.parquet.application.port.out

import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetExportContext
import java.io.Serializable

/** Publication mode supported by the event-log lakehouse pipeline. */
enum class EventLogPublicationMode {
  PARQUET_ONLY,
  ICEBERG_HADOOP,
  ICEBERG_REST,
}

/** One publication attempt over a set of finalized Parquet files. */
data class EventLogPublicationRequest(
    val files: List<EventLogPublishedParquetFile>,
    val exportContext: EventLogParquetExportContext,
) : Serializable {
  init {
    require(files.isNotEmpty()) { "files must not be empty." }
  }
}

/** Summary returned by a publication strategy once files become queryable. */
data class EventLogPublicationReceipt(
    val mode: EventLogPublicationMode,
    val publishedFileCount: Int,
    val metadata: Map<String, String> = emptyMap(),
) : Serializable {
  init {
    require(publishedFileCount >= 0) { "publishedFileCount must be greater than or equal to zero." }
  }
}

/** Final publication boundary used after Parquet promotion succeeds. */
interface EventLogPublicationStrategy {
  val mode: EventLogPublicationMode

  fun publish(request: EventLogPublicationRequest): EventLogPublicationReceipt
}
