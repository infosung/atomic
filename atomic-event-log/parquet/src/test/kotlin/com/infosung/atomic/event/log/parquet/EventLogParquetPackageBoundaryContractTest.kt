package com.infosung.atomic.event.log.parquet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventLogParquetPackageBoundaryContractTest {
  @Test
  fun `parquet legal topology should export application and adapter packages`() {
    assertEquals(
        "com.infosung.atomic.event.log.parquet.application.service.EventLogParquetExportCoordinator",
        requiredClass(
                "com.infosung.atomic.event.log.parquet.application.service.EventLogParquetExportCoordinator")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpool",
        requiredClass("com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpool")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.parquet.adapter.out.store.SpoolBackedEventLogStore",
        requiredClass(
                "com.infosung.atomic.event.log.parquet.adapter.out.store.SpoolBackedEventLogStore")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.parquet.adapter.out.spool.file.FileEventLogSpool",
        requiredClass(
                "com.infosung.atomic.event.log.parquet.adapter.out.spool.file.FileEventLogSpool")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.parquet.adapter.out.publication.ParquetOnlyEventLogPublicationStrategy",
        requiredClass(
                "com.infosung.atomic.event.log.parquet.adapter.out.publication.ParquetOnlyEventLogPublicationStrategy")
            .name,
    )
  }

  @Test
  fun `legacy parquet root types should be removed`() {
    assertMissing("com.infosung.atomic.event.log.parquet.EventLogParquetExportCoordinator")
    assertMissing("com.infosung.atomic.event.log.parquet.EventLogSpool")
    assertMissing("com.infosung.atomic.event.log.parquet.SpoolBackedEventLogStore")
    assertMissing("com.infosung.atomic.event.log.parquet.FileEventLogSpool")
    assertMissing("com.infosung.atomic.event.log.parquet.ParquetOnlyEventLogPublicationStrategy")
  }

  private fun requiredClass(name: String): Class<*> = Class.forName(name)

  private fun assertMissing(name: String) {
    assertFailsWith<ClassNotFoundException> { Class.forName(name) }
  }
}
