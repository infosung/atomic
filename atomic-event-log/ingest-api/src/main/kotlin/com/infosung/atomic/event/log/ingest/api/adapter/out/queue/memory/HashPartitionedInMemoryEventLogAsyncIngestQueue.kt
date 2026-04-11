package com.infosung.atomic.event.log.ingest.api.adapter.out.queue.memory

import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestEnqueueReceipt
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueCheckpoint
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueEntry
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueuePolicy
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue

/** Hash-sharded in-memory async ingest queue that routes batches by serviceId. */
class HashPartitionedInMemoryEventLogAsyncIngestQueue(
    laneCount: Int,
    queuePolicy: EventLogAsyncIngestQueuePolicy = EventLogAsyncIngestQueuePolicy(),
) : EventLogAsyncIngestQueue {
  private val lanes: Map<String, InMemoryEventLogAsyncIngestQueue>

  init {
    require(laneCount > 1) { "laneCount must be greater than 1 for hash-partitioned queue." }
    lanes =
        (0 until laneCount).associate { laneIndex ->
          val laneId = laneIdFor(laneIndex)
          laneId to
              InMemoryEventLogAsyncIngestQueue(
                  laneId = laneId,
                  queuePolicy = queuePolicy,
              )
        }
  }

  override fun enqueue(command: EventLogAsyncIngestCommand): EventLogAsyncIngestEnqueueReceipt =
      laneFor(command.batch.serviceId).enqueue(command)

  override fun laneIds(): List<String> = lanes.keys.sorted()

  override fun pending(laneId: String, limit: Int): List<EventLogAsyncIngestQueueEntry> =
      lane(laneId).pending(laneId = laneId, limit = limit)

  override fun markProcessedThrough(laneId: String, sequenceInclusive: Long) {
    lane(laneId).markProcessedThrough(laneId = laneId, sequenceInclusive = sequenceInclusive)
  }

  override fun checkpoint(laneId: String): EventLogAsyncIngestQueueCheckpoint =
      lane(laneId).checkpoint(laneId = laneId)

  override fun beginShutdown() {
    lanes.values.forEach(InMemoryEventLogAsyncIngestQueue::beginShutdown)
  }

  private fun laneFor(serviceId: String): InMemoryEventLogAsyncIngestQueue {
    val laneIndex = Math.floorMod(serviceId.hashCode(), lanes.size)
    return lanes.getValue(laneIdFor(laneIndex))
  }

  private fun lane(laneId: String): InMemoryEventLogAsyncIngestQueue = lanes.getValue(laneId)

  private fun laneIdFor(index: Int): String = "lane-${index.toString().padStart(3, '0')}"
}
