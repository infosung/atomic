package com.infosung.atomic.event.log.ingest.api.application.service

import com.infosung.atomic.event.log.application.model.EventLogBatch
import com.infosung.atomic.event.log.application.model.EventLogEvent
import com.infosung.atomic.event.log.application.model.EventLogEventIngestResult
import com.infosung.atomic.event.log.application.model.EventLogIngestContext
import com.infosung.atomic.event.log.application.model.EventLogIngestResult
import com.infosung.atomic.event.log.application.model.EventLogStatus
import com.infosung.atomic.event.log.application.port.`in`.IngestEventLogUseCase
import com.infosung.atomic.event.log.domain.ApiEventLogPayload
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestEnqueueReceipt
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiMode
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiResult
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeEvent
import com.infosung.atomic.event.log.ingest.api.application.port.out.AuthorizeEventLogIngestRequestPort
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort
import com.infosung.atomic.event.log.ingest.api.application.port.out.ResolveEventLogIngestContextPort
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class IngestEventLogApiServiceTest {
  @Test
  fun `async mode should authorize resolve context and enqueue without calling core ingest`() {
    val batch = intakeBatch()
    val metadata = requestMetadata()
    val resolvedContext =
        EventLogIngestContext(
            receivedAt = Instant.parse("2026-04-10T00:00:05Z"),
            collectorId = "collector-a",
        )
    val authorizer = RecordingAuthorizer()
    val resolver = RecordingContextResolver(resolvedContext)
    val useCase = RecordingIngestEventLogUseCase()
    val mapper = RecordingMapEventLogIngestIntakeBatchPort(coreBatch())
    val queue = RecordingEventLogAsyncIngestQueue()
    val service =
        IngestEventLogApiService(
            mode = EventLogIngestApiMode.ASYNC,
            authorizeRequestPort = authorizer,
            resolveContextPort = resolver,
            ingestEventLogUseCase = useCase,
            mapEventLogIngestIntakeBatchPort = mapper,
            asyncIngestQueue = queue,
        )

    val actual = service.ingest(batch = batch, requestMetadata = metadata)

    val enqueued = assertIs<EventLogIngestApiResult.Enqueued>(actual)
    assertSame(batch, authorizer.lastBatch)
    assertSame(metadata, authorizer.lastMetadata)
    assertSame(batch, resolver.lastBatch)
    assertSame(metadata, resolver.lastMetadata)
    assertSame(batch, queue.lastCommand?.batch)
    assertEquals(resolvedContext, queue.lastCommand?.context)
    assertNull(useCase.lastBatch)
    assertNull(mapper.lastBatch)
    assertEquals(batch.events.size, enqueued.queuedEventCount)
  }

  @Test
  fun `sync mode should authorize resolve map and delegate to core ingest use case`() {
    val batch = intakeBatch()
    val metadata = requestMetadata()
    val resolvedContext =
        EventLogIngestContext(
            receivedAt = Instant.parse("2026-04-10T00:00:05Z"),
            collectorId = "collector-a",
        )
    val expected =
        EventLogIngestResult(
            accepted = 1,
            duplicate = 0,
            rejected = 0,
            results =
                listOf(
                    EventLogEventIngestResult(
                        eventId = "evt-1",
                        status = EventLogStatus.ACCEPTED,
                    ),
                ),
        )
    val authorizer = RecordingAuthorizer()
    val resolver = RecordingContextResolver(resolvedContext)
    val useCase = RecordingIngestEventLogUseCase(expected)
    val coreBatch = coreBatch()
    val mapper = RecordingMapEventLogIngestIntakeBatchPort(coreBatch)
    val service =
        IngestEventLogApiService(
            mode = EventLogIngestApiMode.SYNC,
            authorizeRequestPort = authorizer,
            resolveContextPort = resolver,
            ingestEventLogUseCase = useCase,
            mapEventLogIngestIntakeBatchPort = mapper,
        )

    val actual = service.ingest(batch = batch, requestMetadata = metadata)

    val completed = assertIs<EventLogIngestApiResult.Completed>(actual)
    assertSame(batch, authorizer.lastBatch)
    assertSame(metadata, authorizer.lastMetadata)
    assertSame(batch, resolver.lastBatch)
    assertSame(metadata, resolver.lastMetadata)
    assertSame(batch, mapper.lastBatch)
    assertSame(coreBatch, useCase.lastBatch)
    assertEquals(resolvedContext, useCase.lastContext)
    assertSame(expected, completed.result)
  }

  private fun intakeBatch(): EventLogIngestIntakeBatch =
      EventLogIngestIntakeBatch(
          serviceId = "totp",
          events =
              listOf(
                  EventLogIngestIntakeEvent(
                      eventId = "evt-1",
                      eventName = "api.request",
                      occurredAt = "2026-04-10T00:00:00Z",
                      platform = "API",
                      platformPayloadJson = """{"httpMethod":"POST","endpoint":"/api/v1/totp"}""",
                  ),
              ),
      )

  private fun coreBatch(): EventLogBatch =
      EventLogBatch(
          serviceId = "totp",
          events =
              listOf(
                  EventLogEvent(
                      eventId = "evt-1",
                      eventName = "api.request",
                      occurredAt = Instant.parse("2026-04-10T00:00:00Z"),
                      platformPayload =
                          ApiEventLogPayload(
                              httpMethod = "POST",
                              endpoint = "/api/v1/totp",
                          ),
                  ),
              ),
      )

  private fun requestMetadata(): EventLogIngestApiRequestMetadata =
      EventLogIngestApiRequestMetadata(
          method = "POST",
          requestUri = "/api/v1/event-logs:batch",
          clientIp = "127.0.0.1",
          userAgent = "JUnit",
          headers = mapOf("x-collector-id" to "collector-a"),
      )

  private class RecordingAuthorizer : AuthorizeEventLogIngestRequestPort {
    var lastBatch: EventLogIngestIntakeBatch? = null
    var lastMetadata: EventLogIngestApiRequestMetadata? = null

    override fun authorize(
        batch: EventLogIngestIntakeBatch,
        requestMetadata: EventLogIngestApiRequestMetadata,
    ) {
      lastBatch = batch
      lastMetadata = requestMetadata
    }
  }

  private class RecordingContextResolver(
      private val resolvedContext: EventLogIngestContext,
  ) : ResolveEventLogIngestContextPort {
    var lastBatch: EventLogIngestIntakeBatch? = null
    var lastMetadata: EventLogIngestApiRequestMetadata? = null

    override fun resolve(
        batch: EventLogIngestIntakeBatch,
        requestMetadata: EventLogIngestApiRequestMetadata,
    ): EventLogIngestContext {
      lastBatch = batch
      lastMetadata = requestMetadata
      return resolvedContext
    }
  }

  private class RecordingIngestEventLogUseCase(
      private val result: EventLogIngestResult =
          EventLogIngestResult(
              accepted = 0,
              duplicate = 0,
              rejected = 0,
              results = emptyList(),
          ),
  ) : IngestEventLogUseCase {
    var lastBatch: EventLogBatch? = null
    var lastContext: EventLogIngestContext? = null

    override fun ingest(
        batch: EventLogBatch,
        context: EventLogIngestContext,
    ): EventLogIngestResult {
      lastBatch = batch
      lastContext = context
      return result
    }
  }

  private class RecordingMapEventLogIngestIntakeBatchPort(
      private val mappedBatch: EventLogBatch,
  ) : MapEventLogIngestIntakeBatchPort {
    var lastBatch: EventLogIngestIntakeBatch? = null

    override fun toCoreBatch(batch: EventLogIngestIntakeBatch): EventLogBatch {
      lastBatch = batch
      return mappedBatch
    }
  }

  private class RecordingEventLogAsyncIngestQueue : EventLogAsyncIngestQueue {
    var lastCommand: EventLogAsyncIngestCommand? = null

    override fun enqueue(command: EventLogAsyncIngestCommand): EventLogAsyncIngestEnqueueReceipt {
      lastCommand = command
      return EventLogAsyncIngestEnqueueReceipt(
          receiptId = command.receiptId,
          laneId = "lane-000",
          sequence = 1L,
          queuedAt = command.enqueuedAt,
      )
    }

    override fun laneIds(): List<String> = listOf("lane-000")

    override fun pending(laneId: String, limit: Int) =
        emptyList<
            com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestQueueEntry>()

    override fun markProcessedThrough(laneId: String, sequenceInclusive: Long) = Unit

    override fun checkpoint(laneId: String) =
        com.infosung.atomic.event.log.ingest.api.application.model
            .EventLogAsyncIngestQueueCheckpoint(laneId = laneId)

    override fun beginShutdown() = Unit
  }
}
