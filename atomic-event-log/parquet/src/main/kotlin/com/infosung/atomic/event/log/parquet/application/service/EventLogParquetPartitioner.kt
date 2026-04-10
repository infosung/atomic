package com.infosung.atomic.event.log.parquet.application.service

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.parquet.application.model.EventLogParquetPartition
import java.time.ZoneOffset

/** Extracts a Parquet/Iceberg partition from one sanitized record. */
class EventLogParquetPartitioner {
  fun partition(record: EventLogRecord): EventLogParquetPartition {
    val occurredAtUtc = record.occurredAt.atOffset(ZoneOffset.UTC)
    return EventLogParquetPartition(
        serviceId = record.serviceId,
        platform = record.platform,
        dt = occurredAtUtc.toLocalDate(),
        hour = occurredAtUtc.hour,
    )
  }
}
