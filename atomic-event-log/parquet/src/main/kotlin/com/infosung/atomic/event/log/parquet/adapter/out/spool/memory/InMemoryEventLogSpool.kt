package com.infosung.atomic.event.log.parquet.adapter.out.spool.memory

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.parquet.application.model.deduplicationKey
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpool
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolAppendReceipt
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolCheckpoint
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpoolEntry
import java.time.Instant

/** In-memory spool for tests and local composition. */
class InMemoryEventLogSpool : EventLogSpool {
  private val log = System.getLogger(InMemoryEventLogSpool::class.java.name)
  private val entries = mutableListOf<EventLogSpoolEntry>()
  private val dedupeIndex = linkedMapOf<String, Long>()
  private var nextSequence = 1L
  private var committedSequence = 0L

  override fun append(records: List<EventLogRecord>): EventLogSpoolAppendReceipt =
      synchronized(this) {
        if (records.isEmpty()) {
          return EventLogSpoolAppendReceipt(
              startSequence = committedSequence,
              endSequence = committedSequence,
              count = 0,
          )
        }
        val startSequence = nextSequence
        val appendedAt = Instant.now()
        records.forEach { record ->
          val sequence = nextSequence++
          entries +=
              EventLogSpoolEntry(sequence = sequence, record = record, appendedAt = appendedAt)
          dedupeIndex[record.deduplicationKey()] = sequence
        }
        val receipt =
            EventLogSpoolAppendReceipt(
                startSequence = startSequence,
                endSequence = nextSequence - 1,
                count = records.size,
            )
        log.log(
            System.Logger.Level.DEBUG,
            "In-memory spool append finished: count={0}, startSequence={1}, endSequence={2}",
            receipt.count,
            receipt.startSequence,
            receipt.endSequence,
        )
        receipt
      }

  override fun pending(limit: Int): List<EventLogSpoolEntry> =
      synchronized(this) {
        entries.asSequence().filter { it.sequence > committedSequence }.take(limit).toList()
      }

  override fun markCommittedThrough(sequenceInclusive: Long) {
    synchronized(this) {
      require(sequenceInclusive >= committedSequence) {
        "sequenceInclusive must not move backwards. committedSequence=$committedSequence, requested=$sequenceInclusive"
      }
      committedSequence = sequenceInclusive
      log.log(
          System.Logger.Level.DEBUG,
          "In-memory spool checkpoint advanced: committedSequence={0}",
          committedSequence,
      )
    }
  }

  override fun checkpoint(): EventLogSpoolCheckpoint =
      synchronized(this) { EventLogSpoolCheckpoint(committedSequence = committedSequence) }

  override fun knownDeduplicationIndex(): Map<String, Long> =
      synchronized(this) { dedupeIndex.toMap() }
}
