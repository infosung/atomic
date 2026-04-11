package com.infosung.atomic.event.log.ingest.api.adapter.out.worker

import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.application.model.EventLogEvent
import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.application.model.EventLogIngestResult
import com.infosung.atomic.event.log.application.port.`in`.IngestEventLogUseCase
import com.infosung.atomic.event.log.domain.ApiEventLogPayload
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestEnqueueReceipt
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueCheckpoint
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueEntry
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeEvent
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort
import com.infosung.atomic.event.log.ingest.api.application.service.ProcessQueuedEventLogBatchesService
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventLogAsyncIngestWorkerLifecycleTest {
  @Test
  fun `worker lifecycle should begin shutdown and drain remaining queue`() {
    val queue = ShutdownDrivenQueue()
    val invocations = AtomicInteger(0)
    val processService =
        ProcessQueuedEventLogBatchesService(
            queue = queue,
            ingestEventLogUseCase =
                object : IngestEventLogUseCase {
                  override fun ingest(
                      batch: EventLogBatch,
                      context: EventLogIngestContext,
                  ): EventLogIngestResult {
                    invocations.incrementAndGet()
                    return EventLogIngestResult(
                        accepted = 1,
                        duplicate = 0,
                        rejected = 0,
                        results = emptyList(),
                    )
                  }
                },
            mapEventLogIngestIntakeBatchPort = intakeMapper(),
        )
    val lifecycle =
        EventLogAsyncIngestWorkerLifecycle(
            processService = processService,
            pollDelay = Duration.ofMillis(20),
            pollLimit = 8,
            shutdownDrainTimeout = Duration.ofMillis(250),
        )

    lifecycle.start()
    Thread.sleep(50)
    assertEquals(0, invocations.get())

    lifecycle.stop()

    assertTrue(queue.shutdownBegun)
    assertEquals(1, invocations.get())
    assertEquals(1L, queue.checkpoint("default").processedSequence)
    assertEquals(0, queue.checkpoint("default").queuedRequestCount)
  }

  @Test
  fun `worker lifecycle should continue draining healthy lane when another lane fails`() {
    val queue = DualLaneAsyncQueue()
    val laneInvocations = mutableMapOf("lane-a" to AtomicInteger(0), "lane-b" to AtomicInteger(0))
    val processService =
        ProcessQueuedEventLogBatchesService(
            queue = queue,
            ingestEventLogUseCase =
                object : IngestEventLogUseCase {
                  override fun ingest(
                      batch: EventLogBatch,
                      context: EventLogIngestContext,
                  ): EventLogIngestResult {
                    val lane = batch.events.single().eventId.removePrefix("evt-")
                    if (lane == "lane-a") {
                      throw IllegalStateException("lane-a failed")
                    }
                    laneInvocations.getValue(lane).incrementAndGet()
                    return EventLogIngestResult(
                        accepted = 1,
                        duplicate = 0,
                        rejected = 0,
                        results = emptyList(),
                    )
                  }
                },
            mapEventLogIngestIntakeBatchPort = intakeMapper(),
        )
    val lifecycle =
        EventLogAsyncIngestWorkerLifecycle(
            processService = processService,
            pollDelay = Duration.ofMillis(20),
            pollLimit = 8,
            shutdownDrainTimeout = Duration.ofMillis(250),
        )

    lifecycle.start()
    eventually { assertTrue(laneInvocations.getValue("lane-b").get() > 0) }
    lifecycle.stop()

    assertEquals(0, laneInvocations.getValue("lane-a").get())
    assertTrue(queue.committedLaneB)
  }

  @Test
  fun `worker lifecycle should skip inline drain when worker is still running after timeout`() {
    val queue = SingleEntryAsyncQueue()
    val invocations = AtomicInteger(0)
    val started = CountDownLatch(1)
    val processService =
        ProcessQueuedEventLogBatchesService(
            queue = queue,
            ingestEventLogUseCase =
                object : IngestEventLogUseCase {
                  override fun ingest(
                      batch: EventLogBatch,
                      context: EventLogIngestContext,
                  ): EventLogIngestResult {
                    invocations.incrementAndGet()
                    started.countDown()
                    val deadline = System.nanoTime() + Duration.ofMillis(200).toNanos()
                    while (System.nanoTime() < deadline) {
                      try {
                        Thread.sleep(10)
                      } catch (_: InterruptedException) {
                        // Simulate a slow, interruption-insensitive dependency.
                      }
                    }
                    return EventLogIngestResult(
                        accepted = 1,
                        duplicate = 0,
                        rejected = 0,
                        results = emptyList(),
                    )
                  }
                },
            mapEventLogIngestIntakeBatchPort = intakeMapper(),
        )
    val lifecycle =
        EventLogAsyncIngestWorkerLifecycle(
            processService = processService,
            pollDelay = Duration.ofMillis(10),
            pollLimit = 8,
            shutdownDrainTimeout = Duration.ofMillis(20),
        )

    lifecycle.start()
    assertTrue(started.await(1, TimeUnit.SECONDS))

    lifecycle.stop()
    Thread.sleep(250)

    assertEquals(1, invocations.get())
  }

  private fun intakeMapper(): MapEventLogIngestIntakeBatchPort = MapEventLogIngestIntakeBatchPort {
    val lane = it.events.single().eventId!!.removePrefix("evt-")
    EventLogBatch(
        serviceId = it.serviceId,
        events =
            listOf(
                EventLogEvent(
                    eventId = "evt-$lane",
                    eventName = "api.request",
                    occurredAt = Instant.parse("2026-04-10T00:00:00Z"),
                    platformPayload =
                        ApiEventLogPayload(
                            httpMethod = "POST",
                            endpoint = "/api/v1/$lane",
                        ),
                ),
            ),
    )
  }

  private fun eventually(block: () -> Unit) {
    repeat(20) {
      runCatching(block).onSuccess {
        return
      }
      Thread.sleep(10)
    }
    block()
  }

  private class ShutdownDrivenQueue : EventLogAsyncIngestQueue {
    private val entry =
        EventLogAsyncIngestQueueEntry(
            sequence = 1L,
            laneId = "default",
            command = laneCommand(laneId = "default"),
        )
    @Volatile var shutdownBegun = false
    @Volatile private var processed = false

    override fun enqueue(command: EventLogAsyncIngestCommand): EventLogAsyncIngestEnqueueReceipt {
      throw UnsupportedOperationException("enqueue is not used in this test")
    }

    override fun laneIds(): List<String> = listOf("default")

    override fun pending(laneId: String, limit: Int): List<EventLogAsyncIngestQueueEntry> =
        if (shutdownBegun && !processed) listOf(entry) else emptyList()

    override fun markProcessedThrough(laneId: String, sequenceInclusive: Long) {
      processed = true
    }

    override fun checkpoint(laneId: String): EventLogAsyncIngestQueueCheckpoint =
        EventLogAsyncIngestQueueCheckpoint(
            laneId = laneId,
            processedSequence = if (processed) 1L else 0L,
            queuedRequestCount = if (processed) 0 else if (shutdownBegun) 1 else 0,
        )

    override fun beginShutdown() {
      shutdownBegun = true
    }
  }

  private class DualLaneAsyncQueue : EventLogAsyncIngestQueue {
    private val laneAEntry =
        EventLogAsyncIngestQueueEntry(
            sequence = 1L,
            laneId = "lane-a",
            command = laneCommand(laneId = "lane-a"),
        )
    private val laneBEntry =
        EventLogAsyncIngestQueueEntry(
            sequence = 1L,
            laneId = "lane-b",
            command = laneCommand(laneId = "lane-b"),
        )
    @Volatile private var laneAProcessed = false
    @Volatile private var laneBProcessed = false
    @Volatile var committedLaneB = false

    override fun enqueue(command: EventLogAsyncIngestCommand): EventLogAsyncIngestEnqueueReceipt {
      throw UnsupportedOperationException("enqueue is not used in this test")
    }

    override fun laneIds(): List<String> = listOf("lane-a", "lane-b")

    override fun pending(laneId: String, limit: Int): List<EventLogAsyncIngestQueueEntry> =
        when (laneId) {
          "lane-a" -> if (laneAProcessed) emptyList() else listOf(laneAEntry)
          "lane-b" -> if (laneBProcessed) emptyList() else listOf(laneBEntry)
          else -> emptyList()
        }

    override fun markProcessedThrough(laneId: String, sequenceInclusive: Long) {
      when (laneId) {
        "lane-a" -> laneAProcessed = true
        "lane-b" -> {
          laneBProcessed = true
          committedLaneB = true
        }
      }
    }

    override fun checkpoint(laneId: String): EventLogAsyncIngestQueueCheckpoint =
        EventLogAsyncIngestQueueCheckpoint(
            laneId = laneId,
            processedSequence =
                when (laneId) {
                  "lane-a" -> if (laneAProcessed) 1L else 0L
                  "lane-b" -> if (laneBProcessed) 1L else 0L
                  else -> 0L
                },
            queuedRequestCount =
                when (laneId) {
                  "lane-a" -> if (laneAProcessed) 0 else 1
                  "lane-b" -> if (laneBProcessed) 0 else 1
                  else -> 0
                },
        )

    override fun beginShutdown() = Unit
  }

  private class SingleEntryAsyncQueue : EventLogAsyncIngestQueue {
    private val entry =
        EventLogAsyncIngestQueueEntry(
            sequence = 1L,
            laneId = "default",
            command = laneCommand(laneId = "default"),
        )
    @Volatile private var processed = false

    override fun enqueue(command: EventLogAsyncIngestCommand): EventLogAsyncIngestEnqueueReceipt {
      throw UnsupportedOperationException("enqueue is not used in this test")
    }

    override fun laneIds(): List<String> = listOf("default")

    override fun pending(laneId: String, limit: Int): List<EventLogAsyncIngestQueueEntry> =
        if (processed) emptyList() else listOf(entry)

    override fun markProcessedThrough(laneId: String, sequenceInclusive: Long) {
      processed = true
    }

    override fun checkpoint(laneId: String): EventLogAsyncIngestQueueCheckpoint =
        EventLogAsyncIngestQueueCheckpoint(
            laneId = laneId,
            processedSequence = if (processed) 1L else 0L,
            queuedRequestCount = if (processed) 0 else 1,
        )

    override fun beginShutdown() = Unit
  }
}

private fun laneCommand(laneId: String): EventLogAsyncIngestCommand =
    EventLogAsyncIngestCommand(
        receiptId = "receipt-$laneId",
        batch =
            EventLogIngestIntakeBatch(
                serviceId = "svc-$laneId",
                events =
                    listOf(
                        EventLogIngestIntakeEvent(
                            eventId = "evt-$laneId",
                            eventName = "api.request",
                            occurredAt = "2026-04-10T00:00:00Z",
                            platform = "API",
                            platformPayloadJson =
                                """{"httpMethod":"POST","endpoint":"/api/v1/$laneId"}""",
                        ),
                    ),
            ),
        context = EventLogIngestContext(receivedAt = Instant.parse("2026-04-10T00:00:01Z")),
        enqueuedAt = Instant.parse("2026-04-10T00:00:02Z"),
    )
