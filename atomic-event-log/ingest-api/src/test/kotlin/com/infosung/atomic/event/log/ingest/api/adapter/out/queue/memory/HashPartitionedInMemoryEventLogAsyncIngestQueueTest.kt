package com.infosung.atomic.event.log.ingest.api.adapter.out.queue.memory

import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueuePolicy
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeEvent
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HashPartitionedInMemoryEventLogAsyncIngestQueueTest {
  @Test
  fun `same service should always route to same lane and checkpoints should stay isolated`() {
    val queue =
        HashPartitionedInMemoryEventLogAsyncIngestQueue(
            laneCount = 4,
            queuePolicy =
                EventLogAsyncIngestQueuePolicy(
                    maxBufferedRequestsPerLane = 8,
                    maxBufferedBytesPerLane = 4_096,
                    enqueueTimeout = Duration.ofMillis(5),
                ),
        )

    val receiptA1 = queue.enqueue(command(serviceId = "totp", receiptId = "receipt-a1"))
    val receiptA2 = queue.enqueue(command(serviceId = "totp", receiptId = "receipt-a2"))
    val anotherServiceId =
        generateSequence(1) { previous -> previous + 1 }
            .map { "service-$it" }
            .first { candidate -> laneIdFor(candidate, laneCount = 4) != receiptA1.laneId }
    val receiptB = queue.enqueue(command(serviceId = anotherServiceId, receiptId = "receipt-b1"))

    assertEquals(receiptA1.laneId, receiptA2.laneId)
    assertTrue(queue.laneIds().contains(receiptA1.laneId))
    assertTrue(queue.laneIds().contains(receiptB.laneId))
    assertNotEquals(receiptA1.laneId, receiptB.laneId)
    assertEquals(2, queue.checkpoint(receiptA1.laneId).queuedRequestCount)
    assertEquals(1, queue.checkpoint(receiptB.laneId).queuedRequestCount)

    queue.markProcessedThrough(laneId = receiptA1.laneId, sequenceInclusive = receiptA2.sequence)

    assertEquals(0, queue.pending(laneId = receiptA1.laneId, limit = 10).size)
    assertEquals(1, queue.pending(laneId = receiptB.laneId, limit = 10).size)
  }

  private fun command(serviceId: String, receiptId: String): EventLogAsyncIngestCommand =
      EventLogAsyncIngestCommand(
          receiptId = receiptId,
          batch =
              EventLogIngestIntakeBatch(
                  serviceId = serviceId,
                  events =
                      listOf(
                          EventLogIngestIntakeEvent(
                              eventId = "$receiptId-event",
                              eventName = "api.request",
                              occurredAt = "2026-04-10T00:00:00Z",
                              platform = "API",
                              platformPayloadJson =
                                  """{"httpMethod":"POST","endpoint":"/api/v1/$serviceId"}""",
                          ),
                      ),
              ),
          context = EventLogIngestContext(receivedAt = Instant.parse("2026-04-10T00:00:01Z")),
          enqueuedAt = Instant.parse("2026-04-10T00:00:02Z"),
      )

  private fun laneIdFor(serviceId: String, laneCount: Int): String {
    val laneIndex = Math.floorMod(serviceId.hashCode(), laneCount)
    return "lane-${laneIndex.toString().padStart(3, '0')}"
  }
}
