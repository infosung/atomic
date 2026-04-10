package com.infosung.atomic.event.log.application.port.out

import com.infosung.atomic.event.log.application.model.EventLogRecord

/** Append result for one validated record. */
enum class EventLogStoreAppendResult {
  ACCEPTED,
  DUPLICATE,
}

/** Persistence port for append-only event-log records. */
fun interface EventLogStore {
  fun append(records: List<EventLogRecord>): List<EventLogStoreAppendResult>
}
