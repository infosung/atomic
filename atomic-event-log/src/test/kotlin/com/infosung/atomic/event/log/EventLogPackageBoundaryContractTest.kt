package com.infosung.atomic.event.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventLogPackageBoundaryContractTest {
  @Test
  fun `core legal topology should export domain application and adapter packages`() {
    assertEquals(
        "com.infosung.atomic.event.log.domain.ClientEventLogPayload",
        requiredClass("com.infosung.atomic.event.log.domain.ClientEventLogPayload").name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.application.service.EventLogIngestionService",
        requiredClass("com.infosung.atomic.event.log.application.service.EventLogIngestionService")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.application.port.in.IngestEventLogUseCase",
        requiredClass("com.infosung.atomic.event.log.application.port.in.IngestEventLogUseCase")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.application.port.out.EventLogStore",
        requiredClass("com.infosung.atomic.event.log.application.port.out.EventLogStore").name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.application.exception.EventLogValidationException",
        requiredClass(
                "com.infosung.atomic.event.log.application.exception.EventLogValidationException")
            .name,
    )
    assertEquals(
        "com.infosung.atomic.event.log.adapter.out.store.InMemoryEventLogStore",
        requiredClass("com.infosung.atomic.event.log.adapter.out.store.InMemoryEventLogStore").name,
    )
  }

  @Test
  fun `legacy root core types should be removed`() {
    assertMissing("com.infosung.atomic.event.log.ClientEventLogPayload")
    assertMissing("com.infosung.atomic.event.log.EventLogIngestionService")
    assertMissing("com.infosung.atomic.event.log.IngestEventLogUseCase")
    assertMissing("com.infosung.atomic.event.log.EventLogStore")
    assertMissing("com.infosung.atomic.event.log.EventLogValidationException")
    assertMissing("com.infosung.atomic.event.log.InMemoryEventLogStore")
  }

  private fun requiredClass(name: String): Class<*> = Class.forName(name)

  private fun assertMissing(name: String) {
    assertFailsWith<ClassNotFoundException> { Class.forName(name) }
  }
}
