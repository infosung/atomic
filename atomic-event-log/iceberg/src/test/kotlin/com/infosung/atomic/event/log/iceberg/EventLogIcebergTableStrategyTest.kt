package com.infosung.atomic.event.log.iceberg

import com.infosung.atomic.event.log.iceberg.adapter.out.table.ServiceScopedEventLogIcebergTableStrategy
import com.infosung.atomic.event.log.iceberg.adapter.out.table.SharedEventLogIcebergTableStrategy
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class EventLogIcebergTableStrategyTest {
  @Test
  fun `shared strategy keeps same table for every service`() {
    val strategy =
        SharedEventLogIcebergTableStrategy(
            namespace = listOf("lakehouse"),
            tableName = "event_logs",
        )

    assertEquals("lakehouse.event_logs", strategy.resolve("totp").qualifiedName())
    assertEquals("lakehouse.event_logs", strategy.resolve("fillingheart").qualifiedName())
  }

  @Test
  fun `service scoped strategy sanitizes service id`() {
    val strategy = ServiceScopedEventLogIcebergTableStrategy(namespace = listOf("lakehouse"))

    assertEquals(
        "lakehouse.filling_heart_event_logs",
        strategy.resolve("Filling Heart").qualifiedName(),
    )
  }
}
