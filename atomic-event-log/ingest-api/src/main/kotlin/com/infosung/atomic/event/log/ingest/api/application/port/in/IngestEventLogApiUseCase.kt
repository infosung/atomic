package com.infosung.atomic.event.log.ingest.api.application.port.`in`

import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiResult
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch

/** Inbound use case for the official HTTP ingest adapter. */
fun interface IngestEventLogApiUseCase {
  fun ingest(
      batch: EventLogIngestIntakeBatch,
      requestMetadata: EventLogIngestApiRequestMetadata,
  ): EventLogIngestApiResult
}
