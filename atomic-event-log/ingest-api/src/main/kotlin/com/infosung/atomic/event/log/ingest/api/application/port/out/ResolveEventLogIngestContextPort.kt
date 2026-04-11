package com.infosung.atomic.event.log.ingest.api.application.port.out

import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch

/** Resolves ingest context from transport-specific request metadata. */
fun interface ResolveEventLogIngestContextPort {
  fun resolve(
      batch: EventLogIngestIntakeBatch,
      requestMetadata: EventLogIngestApiRequestMetadata,
  ): EventLogIngestContext
}
