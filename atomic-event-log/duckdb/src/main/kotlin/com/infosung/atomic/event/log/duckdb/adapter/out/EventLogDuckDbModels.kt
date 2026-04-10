package com.infosung.atomic.event.log.duckdb.adapter.out

import com.infosung.atomic.event.log.domain.EventLogPlatform

/** Parquet dataset root for service-partitioned event-log files. */
data class EventLogDuckDbParquetDataset(
    val rootUri: String,
)

/** Iceberg catalog access configuration used when rendering DuckDB SQL. */
data class EventLogDuckDbIcebergCatalog(
    val alias: String,
    val source: String = alias,
    val endpoint: String? = null,
)

/** One Iceberg metadata file used for DuckDB metadata-based scans. */
data class EventLogDuckDbIcebergMetadataDataset(
    val metadataLocation: String,
)

/** Common filter used by DuckDB helper queries. */
data class EventLogDuckDbFilter(
    val serviceId: String? = null,
    val platform: EventLogPlatform? = null,
    val eventName: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
)

/** Filter for GA-compatible client analytics projection and canned queries. */
data class EventLogDuckDbClientFilter(
    val serviceId: String? = null,
    val platform: EventLogPlatform? = null,
    val appId: String? = null,
    val appVersion: String? = null,
    val releaseChannel: String? = null,
    val screenName: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val eventName: String? = null,
    val dateFrom: String? = null,
    val dateTo: String? = null,
) {
  init {
    require(platform == null || platform.isClientPlatform()) {
      "Client analytics filter platform must be a client platform."
    }
  }
}
