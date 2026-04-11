package com.infosung.atomic.event.log.ingest.api.application.model

import com.infosung.atomic.event.log.application.model.EventLogIngestResult
import java.time.Instant

sealed interface EventLogIngestApiResult {
  val serviceId: String
  val schemaVersion: Int
  val mode: EventLogIngestApiMode
  val processingStatus: String

  data class Enqueued(
      override val serviceId: String,
      override val schemaVersion: Int,
      val receiptId: String,
      val queuedAt: Instant,
      val queuedEventCount: Int,
  ) : EventLogIngestApiResult {
    override val mode: EventLogIngestApiMode = EventLogIngestApiMode.ASYNC
    override val processingStatus: String = "ENQUEUED"
  }

  data class Completed(
      override val serviceId: String,
      override val schemaVersion: Int,
      val result: EventLogIngestResult,
  ) : EventLogIngestApiResult {
    override val mode: EventLogIngestApiMode = EventLogIngestApiMode.SYNC
    override val processingStatus: String = "COMPLETED"
  }
}
