package com.infosung.atomic.event.log.parquet.application.model

import com.infosung.atomic.event.log.domain.EventLogPlatform
import java.io.Serializable
import java.time.LocalDate

/** Stable Parquet partition key. */
data class EventLogParquetPartition(
    val serviceId: String,
    val platform: EventLogPlatform,
    val dt: LocalDate,
    val hour: Int,
) : Serializable {
  init {
    require(hour in 0..23) { "hour must be within 0..23." }
  }
}
