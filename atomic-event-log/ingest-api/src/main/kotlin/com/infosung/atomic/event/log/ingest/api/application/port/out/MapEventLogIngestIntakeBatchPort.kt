package com.infosung.atomic.event.log.ingest.api.application.port.out

import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch

/** Maps shallow intake batches into fully validated core batches. */
fun interface MapEventLogIngestIntakeBatchPort {
  fun toCoreBatch(batch: EventLogIngestIntakeBatch): EventLogBatch
}
