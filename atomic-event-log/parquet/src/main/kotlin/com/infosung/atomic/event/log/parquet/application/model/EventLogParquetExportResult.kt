package com.infosung.atomic.event.log.parquet.application.model

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.domain.EventLogValue
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogPublicationMode
import java.io.Serializable
import java.time.Duration

/** Summary returned after one export cycle. */
data class EventLogParquetExportResult(
    val exportedFileCount: Int,
    val exportedRecordCount: Int,
    val committedThroughSequence: Long,
    val publicationMode: EventLogPublicationMode,
    val publicationMetadata: Map<String, String> = emptyMap(),
) : Serializable

/** Export safety limits used by the Parquet coordinator hot path. */
data class EventLogParquetExportPolicy(
    val maxPendingDrain: Int = 5_000,
    val maxRecordsPerFile: Int = 5_000,
    val maxEstimatedBytesPerFile: Long = Long.MAX_VALUE,
    val maxFlushDuration: Duration? = null,
) : Serializable {
  init {
    require(maxPendingDrain > 0) { "maxPendingDrain must be greater than zero." }
    require(maxRecordsPerFile > 0) { "maxRecordsPerFile must be greater than zero." }
    require(maxEstimatedBytesPerFile > 0) { "maxEstimatedBytesPerFile must be greater than zero." }
    require(maxFlushDuration == null || !maxFlushDuration.isNegative && !maxFlushDuration.isZero) {
      "maxFlushDuration must be greater than zero when configured."
    }
  }
}

internal fun EventLogRecord.deduplicationKey(): String = "$serviceId:$eventId"

internal fun EventLogRecord.estimatedByteSize(): Long {
  var total = 0L
  total += schemaVersion.toString().length
  total += serviceId.length + eventId.length + eventName.length
  total += eventType?.name?.length ?: 0
  total += platform.name.length
  total += actorId?.length ?: 0
  total += traceId?.length ?: 0
  total += tags.sumOf(String::length)
  total += payloadByteSize(platformPayload)
  total += payloadByteSize(businessPayload)
  return total.coerceAtLeast(1L)
}

private fun payloadByteSize(payload: Map<String, EventLogValue>): Long =
    payload.entries.sumOf { (key, value) -> key.length.toLong() + value.estimatedByteSize() }

private fun EventLogValue.estimatedByteSize(): Long =
    when (this) {
      is EventLogValue.Text -> value.length.toLong()
      is EventLogValue.Integer -> Long.SIZE_BYTES.toLong()
      is EventLogValue.Decimal -> value.toPlainString().length.toLong()
      is EventLogValue.Flag -> 1L
    }
