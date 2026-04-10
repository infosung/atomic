package com.infosung.atomic.event.log.iceberg.application.port.out

import com.infosung.atomic.event.log.iceberg.application.model.EventLogIcebergTableId

/** Resolves an Iceberg table for one service boundary. */
fun interface EventLogIcebergTableStrategy {
  fun resolve(serviceId: String): EventLogIcebergTableId
}
