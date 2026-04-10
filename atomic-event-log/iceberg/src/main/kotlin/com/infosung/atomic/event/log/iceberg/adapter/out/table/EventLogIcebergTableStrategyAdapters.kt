package com.infosung.atomic.event.log.iceberg.adapter.out.table

import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergTableId
import com.infosung.atomic.event.log.iceberg.application.port.out.EventLogIcebergTableStrategy

/** Uses one shared Iceberg table and relies on service-based partitioning inside the table. */
class SharedEventLogIcebergTableStrategy(
    private val namespace: List<String>,
    private val tableName: String,
) : EventLogIcebergTableStrategy {
  override fun resolve(serviceId: String): EventLogIcebergTableId =
      EventLogIcebergTableId(namespace = namespace, tableName = tableName)
}

/** Splits Iceberg tables by service while keeping the rest of the lakehouse contract stable. */
class ServiceScopedEventLogIcebergTableStrategy(
    private val namespace: List<String>,
    private val tableSuffix: String = "event_logs",
) : EventLogIcebergTableStrategy {
  override fun resolve(serviceId: String): EventLogIcebergTableId =
      EventLogIcebergTableId(
          namespace = namespace,
          tableName = "${normalize(serviceId)}_${normalize(tableSuffix)}",
      )

  private fun normalize(value: String): String {
    val normalized =
        value
            .trim()
            .lowercase()
            .replace(NON_TABLE_CHARACTER_REGEX, "_")
            .replace(MULTI_UNDERSCORE_REGEX, "_")
            .trim('_')
    require(normalized.isNotBlank()) { "serviceId must contain at least one table-safe character." }
    return normalized
  }

  private companion object {
    val NON_TABLE_CHARACTER_REGEX = Regex("[^a-z0-9]+")
    val MULTI_UNDERSCORE_REGEX = Regex("_+")
  }
}
