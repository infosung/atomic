package com.infosung.atomic.event.log.ingest.api.application.service

import com.infosung.atomic.event.log.application.exception.EventLogErrorCode
import com.infosung.atomic.event.log.application.exception.EventLogValidationException
import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.application.model.EventLogEvent
import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.application.model.EventLogIngestResult
import com.infosung.atomic.event.log.application.port.`in`.IngestEventLogUseCase
import com.infosung.atomic.event.log.domain.ApiEventLogPayload
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueCheckpoint
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueEntry
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeEvent
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessQueuedEventLogBatchesServiceTest {
  @Test
  fun `validation failures should be treated as handled and checkpoint should advance`() {
    val queue =
        RecordingAsyncQueue(
            entriesByLane =
                mapOf(
                    "lane-000" to
                        listOf(entry(sequence = 1, laneId = "lane-000"), entry(2, "lane-000")),
                ),
        )
    val mapper =
        RecordingMapPort(
            mapOf(
                "evt-1" to coreBatch("evt-1"),
                "evt-2" to coreBatch("evt-2"),
            ),
        )
    val useCase =
        RecordingIngestUseCase(
            results = mapOf("evt-1" to successResult()),
            failures =
                mapOf(
                    "evt-2" to
                        EventLogValidationException(
                            code = EventLogErrorCode.EVENT_LOG_PLATFORM_PAYLOAD_INVALID,
                            message = "bad payload",
                        ),
                ),
        )
    val service =
        ProcessQueuedEventLogBatchesService(
            queue = queue,
            ingestEventLogUseCase = useCase,
            mapEventLogIngestIntakeBatchPort = mapper,
        )

    val result = service.drainLaneOnce(laneId = "lane-000", limit = 10)

    assertEquals(1, result.processed)
    assertEquals(1, result.validationRejected)
    assertEquals(0, result.failed)
    assertEquals(2L, result.lastCommittedSequence)
    assertEquals(2L, queue.lastCommittedSequenceByLane["lane-000"])
  }

  @Test
  fun `unexpected failures should stop drain and only commit contiguous processed entries`() {
    val queue =
        RecordingAsyncQueue(
            entriesByLane =
                mapOf(
                    "lane-000" to
                        listOf(
                            entry(sequence = 1, laneId = "lane-000"),
                            entry(sequence = 2, laneId = "lane-000"),
                            entry(sequence = 3, laneId = "lane-000"),
                        ),
                ),
        )
    val mapper =
        RecordingMapPort(
            mapOf(
                "evt-1" to coreBatch("evt-1"),
                "evt-2" to coreBatch("evt-2"),
                "evt-3" to coreBatch("evt-3"),
            ),
        )
    val useCase =
        RecordingIngestUseCase(
            results = mapOf("evt-1" to successResult()),
            failures = mapOf("evt-2" to IllegalStateException("storage down")),
        )
    val service =
        ProcessQueuedEventLogBatchesService(
            queue = queue,
            ingestEventLogUseCase = useCase,
            mapEventLogIngestIntakeBatchPort = mapper,
        )

    val result = service.drainLaneOnce(laneId = "lane-000", limit = 10)

    assertEquals(1, result.processed)
    assertEquals(0, result.validationRejected)
    assertEquals(1, result.failed)
    assertEquals(1L, result.lastCommittedSequence)
    assertEquals(1L, queue.lastCommittedSequenceByLane["lane-000"])
    assertEquals(listOf("evt-1", "evt-2"), useCase.invokedEventIds)
  }

  @Test
  fun `lane-specific drain should not advance other lane checkpoints`() {
    val queue =
        RecordingAsyncQueue(
            entriesByLane =
                mapOf(
                    "lane-000" to listOf(entry(sequence = 1, laneId = "lane-000")),
                    "lane-001" to listOf(entry(sequence = 1, laneId = "lane-001")),
                ),
        )
    val mapper =
        RecordingMapPort(
            mapOf(
                "evt-1" to coreBatch("evt-1"),
            ),
        )
    val useCase = RecordingIngestUseCase(results = mapOf("evt-1" to successResult()))
    val service =
        ProcessQueuedEventLogBatchesService(
            queue = queue,
            ingestEventLogUseCase = useCase,
            mapEventLogIngestIntakeBatchPort = mapper,
        )

    val result = service.drainLaneOnce(laneId = "lane-000", limit = 10)

    assertEquals(1, result.processed)
    assertEquals(1L, queue.lastCommittedSequenceByLane["lane-000"])
    assertEquals(0L, queue.lastCommittedSequenceByLane["lane-001"] ?: 0L)
    assertEquals(listOf("lane-000", "lane-001"), service.laneIds())
  }

  private fun entry(sequence: Long, laneId: String): EventLogAsyncIngestQueueEntry =
      EventLogAsyncIngestQueueEntry(
          sequence = sequence,
          laneId = laneId,
          command =
              EventLogAsyncIngestCommand(
                  receiptId = "receipt-$sequence",
                  batch =
                      EventLogIngestIntakeBatch(
                          serviceId = "totp",
                          events =
                              listOf(
                                  EventLogIngestIntakeEvent(
                                      eventId = "evt-$sequence",
                                      eventName = "api.request",
                                      occurredAt = "2026-04-10T00:00:0${sequence}Z",
                                      platform = "API",
                                      platformPayloadJson =
                                          """{"httpMethod":"POST","endpoint":"/api/v1/totp"}""",
                                  ),
                              ),
                      ),
                  context =
                      EventLogIngestContext(receivedAt = Instant.parse("2026-04-10T00:01:00Z")),
                  enqueuedAt = Instant.parse("2026-04-10T00:00:59Z"),
              ),
      )

  private fun coreBatch(eventId: String): EventLogBatch =
      EventLogBatch(
          serviceId = "totp",
          events =
              listOf(
                  EventLogEvent(
                      eventId = eventId,
                      eventName = "api.request",
                      occurredAt = Instant.parse("2026-04-10T00:00:00Z"),
                      platformPayload =
                          ApiEventLogPayload(httpMethod = "POST", endpoint = "/api/v1/totp"),
                  ),
              ),
      )

  private fun successResult(): EventLogIngestResult =
      EventLogIngestResult(accepted = 1, duplicate = 0, rejected = 0, results = emptyList())

  private class RecordingAsyncQueue(
      private val entriesByLane: Map<String, List<EventLogAsyncIngestQueueEntry>>,
  ) : EventLogAsyncIngestQueue {
    val lastCommittedSequenceByLane = mutableMapOf<String, Long>()

    override fun enqueue(command: EventLogAsyncIngestCommand) =
        throw UnsupportedOperationException("enqueue is not used in this test")

    override fun laneIds(): List<String> = entriesByLane.keys.sorted()

    override fun pending(laneId: String, limit: Int): List<EventLogAsyncIngestQueueEntry> =
        entriesByLane.getValue(laneId).take(limit)

    override fun markProcessedThrough(laneId: String, sequenceInclusive: Long) {
      lastCommittedSequenceByLane[laneId] = sequenceInclusive
    }

    override fun checkpoint(laneId: String): EventLogAsyncIngestQueueCheckpoint =
        EventLogAsyncIngestQueueCheckpoint(
            laneId = laneId,
            processedSequence = lastCommittedSequenceByLane[laneId] ?: 0L,
            queuedRequestCount = entriesByLane.getValue(laneId).size,
        )

    override fun beginShutdown() = Unit
  }

  private class RecordingMapPort(
      private val batches: Map<String, EventLogBatch>,
  ) : MapEventLogIngestIntakeBatchPort {
    override fun toCoreBatch(batch: EventLogIngestIntakeBatch): EventLogBatch {
      val eventId = batch.events.single().eventId!!
      return batches[eventId] ?: error("unexpected eventId=$eventId")
    }
  }

  private class RecordingIngestUseCase(
      private val results: Map<String, EventLogIngestResult>,
      private val failures: Map<String, Throwable> = emptyMap(),
  ) : IngestEventLogUseCase {
    val invokedEventIds = mutableListOf<String>()

    override fun ingest(
        batch: EventLogBatch,
        context: EventLogIngestContext,
    ): EventLogIngestResult {
      val eventId = batch.events.single().eventId
      invokedEventIds += eventId
      failures[eventId]?.let { throw it }
      return results[eventId]
          ?: EventLogIngestResult(accepted = 1, duplicate = 0, rejected = 0, results = emptyList())
    }
  }
}
