package com.infosung.atomic.event.log.iceberg

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventLogIcebergPackageBoundaryContractTest {
  @Test
  fun `iceberg legal topology should export application and adapter packages`() {
    assertEquals(
        "com.infosung.atomic.event.log.iceberg.application.port.out.EventLogIcebergCatalog",
        requiredClass(
                "com.infosung.atomic.event.log.iceberg.application.port.out.EventLogIcebergCatalog")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.iceberg.adapter.out.publication.HadoopCatalogEventLogPublicationStrategy",
        requiredClass(
                "com.infosung.atomic.event.log.iceberg.adapter.out.publication.HadoopCatalogEventLogPublicationStrategy")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.iceberg.adapter.out.table.SharedEventLogIcebergTableStrategy",
        requiredClass(
                "com.infosung.atomic.event.log.iceberg.adapter.out.table.SharedEventLogIcebergTableStrategy")
            .name,
    )
  }

  @Test
  fun `legacy iceberg root types should be removed`() {
    assertMissing("com.infosung.atomic.event.log.iceberg.EventLogIcebergCatalog")
    assertMissing("com.infosung.atomic.event.log.iceberg.HadoopCatalogEventLogPublicationStrategy")
    assertMissing("com.infosung.atomic.event.log.iceberg.SharedEventLogIcebergTableStrategy")
  }

  private fun requiredClass(name: String): Class<*> = Class.forName(name)

  private fun assertMissing(name: String) {
    assertFailsWith<ClassNotFoundException> { Class.forName(name) }
  }
}
