package com.infosung.atomic.event.log.application.exception

/** Stable validation codes for the common event-log envelope. */
enum class EventLogErrorCode {
  EVENT_LOG_REQUEST_INVALID,
  EVENT_LOG_BATCH_TOO_LARGE,
  EVENT_LOG_EVENT_ID_REQUIRED,
  EVENT_LOG_EVENT_NAME_INVALID,
  EVENT_LOG_PLATFORM_PAYLOAD_INVALID,
  EVENT_LOG_BUSINESS_PAYLOAD_INVALID,
}

/** Batch-level validation failure for event-log ingestion. */
class EventLogValidationException(
    val code: EventLogErrorCode,
    override val message: String,
) : RuntimeException(message)
