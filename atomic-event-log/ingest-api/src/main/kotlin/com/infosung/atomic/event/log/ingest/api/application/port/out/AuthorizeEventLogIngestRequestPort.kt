package com.infosung.atomic.event.log.ingest.api.application.port.out

import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch

/** Optional authorization seam for official HTTP ingest requests. */
fun interface AuthorizeEventLogIngestRequestPort {
  fun authorize(
      batch: EventLogIngestIntakeBatch,
      requestMetadata: EventLogIngestApiRequestMetadata,
  )
}
