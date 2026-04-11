package com.infosung.atomic.event.log.ingest.api.application.service

import com.infosung.atomic.event.log.application.port.`in`.IngestEventLogUseCase
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogAsyncIngestCommand
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiMode
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiRequestMetadata
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiResult
import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestIntakeBatch
import com.infosung.atomic.event.log.ingest.api.application.port.`in`.IngestEventLogApiUseCase
import com.infosung.atomic.event.log.ingest.api.application.port.out.AuthorizeEventLogIngestRequestPort
import com.infosung.atomic.event.log.ingest.api.application.port.out.EventLogAsyncIngestQueue
import com.infosung.atomic.event.log.ingest.api.application.port.out.MapEventLogIngestIntakeBatchPort
import com.infosung.atomic.event.log.ingest.api.application.port.out.ResolveEventLogIngestContextPort
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

/** Application service that bridges HTTP request metadata into the core ingest use case. */
class IngestEventLogApiService(
    private val mode: EventLogIngestApiMode,
    private val authorizeRequestPort: AuthorizeEventLogIngestRequestPort,
    private val resolveContextPort: ResolveEventLogIngestContextPort,
    private val ingestEventLogUseCase: IngestEventLogUseCase,
    private val mapEventLogIngestIntakeBatchPort: MapEventLogIngestIntakeBatchPort,
    private val asyncIngestQueue: EventLogAsyncIngestQueue? = null,
) : IngestEventLogApiUseCase {
  private val log = LoggerFactory.getLogger(this::class.java)

  override fun ingest(
      batch: EventLogIngestIntakeBatch,
      requestMetadata: EventLogIngestApiRequestMetadata,
  ): EventLogIngestApiResult {
    log.debug(
        "Event log ingest API service started: serviceId={}, eventCount={}, uri={}, mode={}",
        batch.serviceId,
        batch.events.size,
        requestMetadata.requestUri,
        mode,
    )
    authorizeRequestPort.authorize(batch = batch, requestMetadata = requestMetadata)
    val context = resolveContextPort.resolve(batch = batch, requestMetadata = requestMetadata)
    return when (mode) {
      EventLogIngestApiMode.ASYNC -> enqueueAsync(batch = batch, context = context)
      EventLogIngestApiMode.SYNC -> completeSynchronously(batch = batch, context = context)
    }
  }

  private fun enqueueAsync(
      batch: EventLogIngestIntakeBatch,
      context: com.infosung.atomic.event.log.application.model.EventLogIngestContext,
  ): EventLogIngestApiResult.Enqueued {
    val queue =
        asyncIngestQueue
            ?: throw IllegalStateException("ASYNC ingest mode requires EventLogAsyncIngestQueue.")
    val queuedAt = Instant.now()
    val receipt =
        queue.enqueue(
            EventLogAsyncIngestCommand(
                receiptId = UUID.randomUUID().toString(),
                batch = batch,
                context = context,
                enqueuedAt = queuedAt,
            ),
        )
    log.debug(
        "Event log ingest API service enqueued batch: serviceId={}, laneId={}, receiptId={}, sequence={}",
        batch.serviceId,
        receipt.laneId,
        receipt.receiptId,
        receipt.sequence,
    )
    return EventLogIngestApiResult.Enqueued(
        serviceId = batch.serviceId,
        schemaVersion = batch.schemaVersion,
        receiptId = receipt.receiptId,
        queuedAt = receipt.queuedAt,
        queuedEventCount = batch.events.size,
    )
  }

  private fun completeSynchronously(
      batch: EventLogIngestIntakeBatch,
      context: com.infosung.atomic.event.log.application.model.EventLogIngestContext,
  ): EventLogIngestApiResult.Completed {
    val coreBatch = mapEventLogIngestIntakeBatchPort.toCoreBatch(batch)
    val result = ingestEventLogUseCase.ingest(batch = coreBatch, context = context)
    log.debug(
        "Event log ingest API service finished synchronously: serviceId={}, accepted={}, duplicate={}, rejected={}",
        batch.serviceId,
        result.accepted,
        result.duplicate,
        result.rejected,
    )
    return EventLogIngestApiResult.Completed(
        serviceId = batch.serviceId,
        schemaVersion = batch.schemaVersion,
        result = result,
    )
  }
}
