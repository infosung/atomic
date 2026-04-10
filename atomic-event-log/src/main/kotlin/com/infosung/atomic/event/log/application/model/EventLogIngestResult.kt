package com.infosung.atomic.event.log.application.model

import com.infosung.atomic.event.log.application.exception.EventLogErrorCode
import java.io.Serializable

/** Per-event status returned by the ingest service. */
enum class EventLogStatus {
  ACCEPTED,
  DUPLICATE,
  REJECTED,
}

/** Per-event ingest outcome. */
data class EventLogEventIngestResult(
    val eventId: String,
    val status: EventLogStatus,
    val code: EventLogErrorCode? = null,
) : Serializable

/** Batch ingest summary. */
data class EventLogIngestResult(
    val accepted: Int,
    val duplicate: Int,
    val rejected: Int,
    val results: List<EventLogEventIngestResult>,
) : Serializable
