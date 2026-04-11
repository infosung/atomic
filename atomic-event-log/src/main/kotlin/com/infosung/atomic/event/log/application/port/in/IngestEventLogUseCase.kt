package com.infosung.atomic.event.log.application.port.`in`

import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.application.model.EventLogIngestResult

/** Primary ingest use-case port for the shared event-log core. */
fun interface IngestEventLogUseCase {
  fun ingest(
      batch: EventLogBatch,
      context: EventLogIngestContext,
  ): EventLogIngestResult
}
