package com.infosung.atomic.event.log.iceberg.application.model

/** Stable Iceberg table identity used by lakehouse adapters. */
data class EventLogIcebergTableId(
    val namespace: List<String>,
    val tableName: String,
) {
  init {
    require(namespace.all { it.isNotBlank() }) { "namespace entries must be non-blank." }
    require(tableName.isNotBlank()) { "tableName must be non-blank." }
  }

  fun qualifiedName(catalogAlias: String? = null): String =
      buildList {
            if (!catalogAlias.isNullOrBlank()) {
              add(catalogAlias)
            }
            addAll(namespace)
            add(tableName)
          }
          .joinToString(".")
}
