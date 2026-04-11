package com.infosung.atomic.event.log.ingest.api.adapter.out.queue.memory

import com.infosung.atomic.event.log.ingest.api.application.exception.EventLogAsyncIngestQueueRejectedException
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestEnqueueReceipt
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueCheckpoint
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueEntry
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueuePolicy
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeEvent
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Single-lane in-memory queue used by async ingest mode. */
class InMemoryEventLogAsyncIngestQueue(
    private val laneId: String = DEFAULT_LANE_ID,
    private val queuePolicy: EventLogAsyncIngestQueuePolicy = EventLogAsyncIngestQueuePolicy(),
) : EventLogAsyncIngestQueue {
  private val log = System.getLogger(InMemoryEventLogAsyncIngestQueue::class.java.name)
  private val lock = ReentrantLock()
  private val capacityChanged = lock.newCondition()
  private val pendingEntries = ArrayDeque<PendingEntry>()
  private var nextSequence = 1L
  private var processedSequence = 0L
  private var queuedEstimatedBytes = 0L
  private var accepting = true

  override fun enqueue(command: EventLogAsyncIngestCommand): EventLogAsyncIngestEnqueueReceipt =
      lock.withLock {
        val estimatedBytes = command.estimatedByteSize()
        val deadlineNanos = System.nanoTime() + queuePolicy.enqueueTimeout.toNanos()
        while (accepting && !hasCapacityFor(estimatedBytes)) {
          var remainingNanos = deadlineNanos - System.nanoTime()
          if (remainingNanos <= 0) {
            rejectSaturated()
          }
          try {
            remainingNanos = capacityChanged.awaitNanos(remainingNanos)
          } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw EventLogAsyncIngestQueueRejectedException(
                "Async ingest queue wait was interrupted before capacity became available.",
            )
          }
        }
        if (!accepting) {
          throw EventLogAsyncIngestQueueRejectedException(
              "Async ingest queue is shutting down and cannot accept new requests.",
          )
        }
        if (!hasCapacityFor(estimatedBytes)) {
          rejectSaturated()
        }
        val entry =
            EventLogAsyncIngestQueueEntry(
                sequence = nextSequence++,
                laneId = laneId,
                command = command,
            )
        pendingEntries += PendingEntry(entry = entry, estimatedBytes = estimatedBytes)
        queuedEstimatedBytes += estimatedBytes
        log.log(
            System.Logger.Level.DEBUG,
            "Async ingest queue enqueued batch: laneId={0}, receiptId={1}, sequence={2}, queuedRequests={3}, queuedBytes={4}",
            laneId,
            command.receiptId,
            entry.sequence,
            pendingEntries.size,
            queuedEstimatedBytes,
        )
        EventLogAsyncIngestEnqueueReceipt(
            receiptId = command.receiptId,
            laneId = laneId,
            sequence = entry.sequence,
            queuedAt = command.enqueuedAt,
        )
      }

  override fun laneIds(): List<String> = listOf(laneId)

  override fun pending(laneId: String, limit: Int): List<EventLogAsyncIngestQueueEntry> =
      lock.withLock {
        requireLane(laneId)
        pendingEntries.asSequence().take(limit.coerceAtLeast(0)).map(PendingEntry::entry).toList()
      }

  override fun markProcessedThrough(laneId: String, sequenceInclusive: Long) {
    lock.withLock {
      requireLane(laneId)
      require(sequenceInclusive >= processedSequence) {
        "sequenceInclusive must not move backwards. processedSequence=$processedSequence, requested=$sequenceInclusive"
      }
      if (sequenceInclusive == processedSequence) {
        return
      }
      var removedCount = 0
      while (pendingEntries.firstOrNull()?.entry?.sequence?.let { it <= sequenceInclusive } ==
          true) {
        val removed = pendingEntries.removeFirst()
        queuedEstimatedBytes = (queuedEstimatedBytes - removed.estimatedBytes).coerceAtLeast(0L)
        removedCount += 1
      }
      processedSequence = sequenceInclusive
      capacityChanged.signalAll()
      log.log(
          System.Logger.Level.DEBUG,
          "Async ingest queue checkpoint advanced: laneId={0}, processedSequence={1}, removed={2}, queuedRequests={3}, queuedBytes={4}",
          this.laneId,
          processedSequence,
          removedCount,
          pendingEntries.size,
          queuedEstimatedBytes,
      )
    }
  }

  override fun checkpoint(laneId: String): EventLogAsyncIngestQueueCheckpoint =
      lock.withLock {
        requireLane(laneId)
        EventLogAsyncIngestQueueCheckpoint(
            laneId = this.laneId,
            processedSequence = processedSequence,
            queuedRequestCount = pendingEntries.size,
            queuedEstimatedBytes = queuedEstimatedBytes,
        )
      }

  override fun beginShutdown() {
    lock.withLock {
      if (!accepting) {
        return
      }
      accepting = false
      capacityChanged.signalAll()
      log.log(
          System.Logger.Level.DEBUG,
          "Async ingest queue stopped accepting new requests: laneId={0}, queuedRequests={1}, queuedBytes={2}",
          laneId,
          pendingEntries.size,
          queuedEstimatedBytes,
      )
    }
  }

  private fun hasCapacityFor(estimatedBytes: Long): Boolean =
      pendingEntries.size < queuePolicy.maxBufferedRequestsPerLane &&
          queuedEstimatedBytes + estimatedBytes <= queuePolicy.maxBufferedBytesPerLane

  private fun requireLane(requestedLaneId: String) {
    require(requestedLaneId == laneId) {
      "Unexpected laneId=$requestedLaneId for in-memory queue lane=$laneId"
    }
  }

  private fun rejectSaturated(): Nothing {
    log.log(
        System.Logger.Level.WARNING,
        "Async ingest queue rejected request because capacity is exhausted: laneId={0}, queuedRequests={1}, queuedBytes={2}",
        laneId,
        pendingEntries.size,
        queuedEstimatedBytes,
    )
    throw EventLogAsyncIngestQueueRejectedException(
        "Async ingest queue is saturated. Increase capacity or reduce request rate.",
    )
  }

  private data class PendingEntry(
      val entry: EventLogAsyncIngestQueueEntry,
      val estimatedBytes: Long,
  )

  companion object {
    const val DEFAULT_LANE_ID: String = "default"
  }
}

private fun EventLogAsyncIngestCommand.estimatedByteSize(): Long {
  var total = receiptId.length.toLong()
  total += batch.estimatedByteSize()
  total += context.collectorId?.length ?: 0
  total += context.receivedAt?.toString()?.length ?: 0
  total += enqueuedAt.toString().length
  return total.coerceAtLeast(1L)
}

private fun EventLogIngestIntakeBatch.estimatedByteSize(): Long {
  var total = serviceId.length.toLong() + schemaVersion.toString().length
  total += events.sumOf(EventLogIngestIntakeEvent::estimatedByteSize)
  return total.coerceAtLeast(1L)
}

private fun EventLogIngestIntakeEvent.estimatedByteSize(): Long {
  var total = 0L
  total += eventId?.length ?: 0
  total += eventName?.length ?: 0
  total += occurredAt?.length ?: 0
  total += platform?.length ?: 0
  total += platformPayloadJson?.length ?: 0
  total += eventType?.length ?: 0
  total += actorId?.length ?: 0
  total += traceId?.length ?: 0
  total += businessPayloadJson?.length ?: 0
  total += tags.sumOf(String::length)
  return total.coerceAtLeast(1L)
}
