package com.infosung.atomic.event.log.parquet.application.port.out

import com.infosung.atomic.event.log.application.model.EventLogRecord
import java.io.Serializable
import java.time.Instant

/** One durable spool entry waiting for export. */
data class EventLogSpoolEntry(
    val sequence: Long,
    val record: EventLogRecord,
    val appendedAt: Instant,
) : Serializable

/** Append receipt returned by a spool implementation. */
data class EventLogSpoolAppendReceipt(
    val startSequence: Long,
    val endSequence: Long,
    val count: Int,
) : Serializable

/** Durable checkpoint for a spool implementation. */
data class EventLogSpoolCheckpoint(
    val committedSequence: Long = 0,
) : Serializable

/** Durable replay boundary used by the Parquet export layer. */
interface EventLogSpool {
  fun append(records: List<EventLogRecord>): EventLogSpoolAppendReceipt

  fun pending(limit: Int = Int.MAX_VALUE): List<EventLogSpoolEntry>

  fun markCommittedThrough(sequenceInclusive: Long)

  fun checkpoint(): EventLogSpoolCheckpoint

  fun knownDeduplicationIndex(): Map<String, Long>
}
