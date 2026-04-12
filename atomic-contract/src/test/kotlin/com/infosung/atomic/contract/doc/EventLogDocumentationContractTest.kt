package com.infosung.atomic.contract.doc

import kotlin.test.Test
import kotlin.test.assertTrue

class EventLogDocumentationContractTest {
  @Test
  fun `readme should expose the collector, client, and lakehouse guides`() {
    val markdown = DocumentationContractFixtures.read("README.md")

    assertTrue(markdown.contains("[atomic.event.log Guide](docs/usage/atomic-event-log.md)"))
    assertTrue(
        markdown.contains("[atomic.event.log Client Guide](docs/usage/atomic-event-log-client.md)"))
    assertTrue(
        markdown.contains(
            "[atomic.event.log Lakehouse Guide](docs/usage/atomic-event-log-lakehouse.md)"))
  }

  @Test
  fun `collector guide should document the implemented intake contract and host seams`() {
    val markdown = DocumentationContractFixtures.read("docs/usage/atomic-event-log.md")

    listOf(
            "/api/v1/event-logs:batch",
            "202 Accepted",
            "ASYNC",
            "EVENT_LOG_INGEST_QUEUE_OVERFLOW",
            "AuthorizeEventLogIngestRequestPort",
            "AtomicSpringWebEventLogSaver",
            "SpoolBackedEventLogStore",
            "EventLogParquetExportCoordinator",
        )
        .forEach { marker ->
          assertTrue(markdown.contains(marker), "collector guide should mention $marker")
        }
  }

  @Test
  fun `client guide should document the shared client envelope and ga aligned conventions`() {
    val markdown = DocumentationContractFixtures.read("docs/usage/atomic-event-log-client.md")

    listOf(
            "schemaVersion",
            "serviceId",
            "userPseudoId",
            "sessionId",
            "businessPayload",
            "screen_view",
            "app_exception",
        )
        .forEach { marker ->
          assertTrue(markdown.contains(marker), "client guide should mention $marker")
        }
  }

  @Test
  fun `lakehouse guide should document the supported storage modes and duckdb view`() {
    val markdown = DocumentationContractFixtures.read("docs/usage/atomic-event-log-lakehouse.md")

    listOf(
            "Parquet-only",
            "HadoopCatalog",
            "REST catalog",
            "client_ga4_compat_events_v1",
            "service_id=",
            "platform=",
        )
        .forEach { marker ->
          assertTrue(markdown.contains(marker), "lakehouse guide should mention $marker")
        }
  }
}
