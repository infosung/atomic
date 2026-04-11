package com.infosung.atomic.event.log.ingest.api.adapter.out.queue.memory

import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.ingest.api.application.exception.EventLogAsyncIngestQueueRejectedException
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueuePolicy
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeEvent
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InMemoryEventLogAsyncIngestQueueTest {
  @Test
  fun `queue should keep pending entries in memory and release capacity after checkpoint`() {
    val queue =
        InMemoryEventLogAsyncIngestQueue(
            laneId = "default",
            queuePolicy =
                EventLogAsyncIngestQueuePolicy(
                    maxBufferedRequestsPerLane = 8,
                    maxBufferedBytesPerLane = 4_096,
                    enqueueTimeout = Duration.ofMillis(5),
                ),
        )

    val receipt1 = queue.enqueue(command(receiptId = "receipt-1"))
    val receipt2 = queue.enqueue(command(receiptId = "receipt-2"))

    assertEquals(2, queue.pending(laneId = "default", limit = 10).size)
    assertEquals(2, queue.checkpoint(laneId = "default").queuedRequestCount)
    assertTrue(queue.checkpoint(laneId = "default").queuedEstimatedBytes > 0)

    queue.markProcessedThrough(laneId = "default", sequenceInclusive = receipt1.sequence)

    val checkpointAfterFirstCommit = queue.checkpoint(laneId = "default")
    assertEquals(1, queue.pending(laneId = "default", limit = 10).size)
    assertEquals(1, checkpointAfterFirstCommit.queuedRequestCount)
    assertEquals(receipt1.sequence, checkpointAfterFirstCommit.processedSequence)

    queue.markProcessedThrough(laneId = "default", sequenceInclusive = receipt2.sequence)

    val checkpointAfterSecondCommit = queue.checkpoint(laneId = "default")
    assertEquals(0, queue.pending(laneId = "default", limit = 10).size)
    assertEquals(0, checkpointAfterSecondCommit.queuedRequestCount)
    assertEquals(0L, checkpointAfterSecondCommit.queuedEstimatedBytes)
  }

  @Test
  fun `queue should reject when byte budget is exhausted`() {
    val queue =
        InMemoryEventLogAsyncIngestQueue(
            laneId = "default",
            queuePolicy =
                EventLogAsyncIngestQueuePolicy(
                    maxBufferedRequestsPerLane = 8,
                    maxBufferedBytesPerLane = 500,
                    enqueueTimeout = Duration.ofMillis(5),
                ),
        )

    queue.enqueue(command(receiptId = "receipt-1", repeatedPayloadSize = 200))

    assertFailsWith<EventLogAsyncIngestQueueRejectedException> {
      queue.enqueue(command(receiptId = "receipt-2", repeatedPayloadSize = 200))
    }
  }

  @Test
  fun `queue should reject new entries after shutdown begins`() {
    val queue =
        InMemoryEventLogAsyncIngestQueue(
            laneId = "default",
            queuePolicy =
                EventLogAsyncIngestQueuePolicy(
                    maxBufferedRequestsPerLane = 8,
                    maxBufferedBytesPerLane = 4_096,
                    enqueueTimeout = Duration.ofMillis(5),
                ),
        )

    queue.beginShutdown()

    assertFailsWith<EventLogAsyncIngestQueueRejectedException> {
      queue.enqueue(command(receiptId = "receipt-1"))
    }
  }

  @Test
  fun `queue should accept waiting enqueue after capacity is released`() {
    val queue =
        InMemoryEventLogAsyncIngestQueue(
            laneId = "default",
            queuePolicy =
                EventLogAsyncIngestQueuePolicy(
                    maxBufferedRequestsPerLane = 1,
                    maxBufferedBytesPerLane = 4_096,
                    enqueueTimeout = Duration.ofMillis(100),
                ),
        )
    val firstReceipt = queue.enqueue(command(receiptId = "receipt-1"))

    val releaser =
        thread(start = true) {
          Thread.sleep(20)
          queue.markProcessedThrough(laneId = "default", sequenceInclusive = firstReceipt.sequence)
        }

    val secondReceipt = queue.enqueue(command(receiptId = "receipt-2"))
    releaser.join()

    assertEquals("receipt-2", secondReceipt.receiptId)
    assertEquals(1, queue.pending(laneId = "default", limit = 10).size)
  }

  private fun command(
      receiptId: String,
      repeatedPayloadSize: Int = 16,
  ): EventLogAsyncIngestCommand =
      EventLogAsyncIngestCommand(
          receiptId = receiptId,
          batch =
              EventLogIngestIntakeBatch(
                  serviceId = "totp",
                  events =
                      listOf(
                          EventLogIngestIntakeEvent(
                              eventId = "$receiptId-event",
                              eventName = "api.request",
                              occurredAt = "2026-04-10T00:00:00Z",
                              platform = "API",
                              platformPayloadJson =
                                  """{"httpMethod":"POST","endpoint":"/api/v1/totp","query":"${"q".repeat(repeatedPayloadSize)}"}""",
                          ),
                      ),
              ),
          context = EventLogIngestContext(receivedAt = Instant.parse("2026-04-10T00:00:01Z")),
          enqueuedAt = Instant.parse("2026-04-10T00:00:02Z"),
      )
}
