package com.infosung.atomic.event.log.ingest.api.adapter.out.context

import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.port.out.ResolveEventLogIngestContextPort
import java.time.Instant
import org.slf4j.LoggerFactory

/** Default transport-to-core context resolver for the official HTTP ingest API. */
class DefaultEventLogIngestContextResolver(
    private val collectorIdHeaderName: String? = null,
) : ResolveEventLogIngestContextPort {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun resolve(
      batch: EventLogIngestIntakeBatch,
      requestMetadata: EventLogIngestApiRequestMetadata,
  ): EventLogIngestContext {
    val normalizedHeaderName =
        collectorIdHeaderName?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
    val collectorId = normalizedHeaderName?.let { requestMetadata.headers[it] }
    log.debug(
        "Resolved event log ingest context: serviceId={}, collectorIdPresent={}",
        batch.serviceId,
        collectorId != null,
    )
    return EventLogIngestContext(
        receivedAt = Instant.now(),
        collectorId = collectorId,
    )
  }
}
