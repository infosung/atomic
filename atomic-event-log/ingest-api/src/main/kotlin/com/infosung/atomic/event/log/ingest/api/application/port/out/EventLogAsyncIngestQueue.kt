package com.infosung.atomic.event.log.ingest.api.application.port.out

import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestEnqueueReceipt
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueCheckpoint
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueEntry

/** In-memory intake queue used by async ingest mode. */
interface EventLogAsyncIngestQueue {
  fun enqueue(command: EventLogAsyncIngestCommand): EventLogAsyncIngestEnqueueReceipt

  fun laneIds(): List<String>

  fun pending(laneId: String, limit: Int = Int.MAX_VALUE): List<EventLogAsyncIngestQueueEntry>

  fun markProcessedThrough(laneId: String, sequenceInclusive: Long)

  fun checkpoint(laneId: String): EventLogAsyncIngestQueueCheckpoint

  fun beginShutdown()
}
