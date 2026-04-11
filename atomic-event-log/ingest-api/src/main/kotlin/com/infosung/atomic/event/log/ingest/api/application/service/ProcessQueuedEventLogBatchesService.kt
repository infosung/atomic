package com.infosung.atomic.event.log.ingest.api.application.service

import com.infosung.atomic.event.log.application.exception.EventLogValidationException
import com.infosung.atomic.event.log.application.port.`in`.IngestEventLogUseCase
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestDrainResult
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort
import org.slf4j.LoggerFactory

/** Background processor that drains durable async queue entries into the core ingest use case. */
class ProcessQueuedEventLogBatchesService(
    private val queue: EventLogAsyncIngestQueue,
    private val ingestEventLogUseCase: IngestEventLogUseCase,
    private val mapEventLogIngestIntakeBatchPort: MapEventLogIngestIntakeBatchPort,
) {
  private val log = LoggerFactory.getLogger(this::class.java)

  fun laneIds(): List<String> = queue.laneIds()

  fun checkpoint(laneId: String) = queue.checkpoint(laneId = laneId)

  fun beginShutdown() {
    queue.beginShutdown()
  }

  fun drainLaneOnce(laneId: String, limit: Int): EventLogAsyncIngestDrainResult {
    if (limit <= 0) {
      return EventLogAsyncIngestDrainResult(
          laneId = laneId,
          processed = 0,
          validationRejected = 0,
          failed = 0,
          lastCommittedSequence = queue.checkpoint(laneId = laneId).processedSequence,
      )
    }
    val pending = queue.pending(laneId = laneId, limit = limit)
    if (pending.isEmpty()) {
      return EventLogAsyncIngestDrainResult(
          laneId = laneId,
          processed = 0,
          validationRejected = 0,
          failed = 0,
          lastCommittedSequence = queue.checkpoint(laneId = laneId).processedSequence,
      )
    }

    var processed = 0
    var validationRejected = 0
    var failed = 0
    var lastCommittedSequence = queue.checkpoint(laneId = laneId).processedSequence

    for (entry in pending) {
      try {
        val batch = mapEventLogIngestIntakeBatchPort.toCoreBatch(entry.command.batch)
        ingestEventLogUseCase.ingest(
            batch = batch,
            context = entry.command.context,
        )
        processed += 1
        lastCommittedSequence = entry.sequence
      } catch (e: EventLogValidationException) {
        validationRejected += 1
        lastCommittedSequence = entry.sequence
        log.warn(
            "Async event-log batch rejected during background processing: laneId={}, receiptId={}, serviceId={}, code={}, message={}",
            laneId,
            entry.command.receiptId,
            entry.command.batch.serviceId,
            e.code,
            e.message,
        )
      } catch (e: Exception) {
        failed += 1
        log.error(
            "Async event-log batch processing failed: laneId={}, receiptId={}, serviceId={}, sequence={}",
            laneId,
            entry.command.receiptId,
            entry.command.batch.serviceId,
            entry.sequence,
            e,
        )
        break
      }
    }

    if (lastCommittedSequence > queue.checkpoint(laneId = laneId).processedSequence) {
      queue.markProcessedThrough(laneId = laneId, sequenceInclusive = lastCommittedSequence)
    }
    log.debug(
        "Async event-log queue drain finished: laneId={}, processed={}, validationRejected={}, failed={}, committedThrough={}",
        laneId,
        processed,
        validationRejected,
        failed,
        lastCommittedSequence,
    )
    return EventLogAsyncIngestDrainResult(
        laneId = laneId,
        processed = processed,
        validationRejected = validationRejected,
        failed = failed,
        lastCommittedSequence = lastCommittedSequence,
    )
  }
}
