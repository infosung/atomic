package com.infosung.atomic.event.log.ingest.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventLogIngestApiPackageBoundaryContractTest {
  @Test
  fun `legal topology should export application adapter and autoconfigure packages`() {
    assertEquals(
        "com.infosung.atomic.event.log.ingest.api.application.port.in.IngestEventLogApiUseCase",
        requiredClass(
                "com.infosung.atomic.event.log.ingest.api.application.port.in.IngestEventLogApiUseCase")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.ingest.api.application.port.out.AuthorizeEventLogIngestRequestPort",
        requiredClass(
                "com.infosung.atomic.event.log.ingest.api.application.port.out.AuthorizeEventLogIngestRequestPort")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort",
        requiredClass(
                "com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.ingest.api.application.service.IngestEventLogApiService",
        requiredClass(
                "com.infosung.atomic.event.log.ingest.api.application.service.IngestEventLogApiService")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.ingest.api.application.service.ProcessQueuedEventLogBatchesService",
        requiredClass(
                "com.infosung.atomic.event.log.ingest.api.application.service.ProcessQueuedEventLogBatchesService")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.ingest.api.adapter.in.web.EventLogIngestController",
        requiredClass(
                "com.infosung.atomic.event.log.ingest.api.adapter.in.web.EventLogIngestController")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.ingest.api.autoconfigure.AtomicEventLogIngestApiAutoConfiguration",
        requiredClass(
                "com.infosung.atomic.event.log.ingest.api.autoconfigure.AtomicEventLogIngestApiAutoConfiguration")
            .name,
    )
  }

  @Test
  fun `legacy root ingest api types should be removed`() {
    assertMissing("com.infosung.atomic.event.log.ingest.api.EventLogIngestController")
    assertMissing("com.infosung.atomic.event.log.ingest.api.IngestEventLogApiService")
    assertMissing(
        "com.infosung.atomic.event.log.ingest.api.AtomicEventLogIngestApiAutoConfiguration")
  }

  private fun requiredClass(name: String): Class<*> = Class.forName(name)

  private fun assertMissing(name: String) {
    assertFailsWith<ClassNotFoundException> { Class.forName(name) }
  }
}
