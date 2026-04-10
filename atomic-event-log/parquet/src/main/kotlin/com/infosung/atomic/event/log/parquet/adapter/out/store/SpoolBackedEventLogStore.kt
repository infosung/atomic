package com.infosung.atomic.event.log.parquet.adapter.out.store

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.application.port.out.EventLogStore
import com.infosung.atomic.event.log.application.port.out.EventLogStoreAppendResult
import com.infosung.atomic.event.log.parquet.application.model.EventLogDeduplicationPolicy
import com.infosung.atomic.event.log.parquet.application.model.deduplicationKey
import com.infosung.atomic.event.log.parquet.application.port.out.EventLogSpool

/** Practical store implementation backed by a replayable spool. */
class SpoolBackedEventLogStore(
    private val spool: EventLogSpool,
    private val deduplicationPolicy: EventLogDeduplicationPolicy = EventLogDeduplicationPolicy(),
) : EventLogStore {
  private val log = System.getLogger(SpoolBackedEventLogStore::class.java.name)
  private val knownKeys = spool.knownDeduplicationIndex().toMutableMap()

  override fun append(records: List<EventLogRecord>): List<EventLogStoreAppendResult> =
      synchronized(this) {
        pruneKnownKeys()
        val acceptedRecords = mutableListOf<EventLogRecord>()
        val acceptedKeys = HashSet<String>(records.size)
        val results =
            records.map { record ->
              val key = record.deduplicationKey()
              if (knownKeys.contains(key) || !acceptedKeys.add(key)) {
                EventLogStoreAppendResult.DUPLICATE
              } else {
                acceptedRecords += record
                EventLogStoreAppendResult.ACCEPTED
              }
            }

        if (acceptedRecords.isNotEmpty()) {
          val receipt = spool.append(acceptedRecords)
          acceptedRecords.forEachIndexed { index, record ->
            knownKeys[record.deduplicationKey()] = receipt.startSequence + index
          }
          pruneKnownKeys()
        }
        log.log(
            System.Logger.Level.DEBUG,
            "Spool-backed store append finished: count={0}, accepted={1}, duplicate={2}, knownKeyCount={3}, committedSequence={4}",
            records.size,
            results.count { it == EventLogStoreAppendResult.ACCEPTED },
            results.count { it == EventLogStoreAppendResult.DUPLICATE },
            knownKeys.size,
            spool.checkpoint().committedSequence,
        )
        results
      }

  private fun pruneKnownKeys() {
    if (knownKeys.isEmpty()) {
      return
    }
    val committedSequence = spool.checkpoint().committedSequence
    val committedRetentionFloor =
        (committedSequence - deduplicationPolicy.retainedCommittedSequenceLag).coerceAtLeast(0L)
    val removableKeys =
        knownKeys.entries
            .asSequence()
            .filter { it.value <= committedRetentionFloor }
            .map { it.key }
            .toMutableList()
    removableKeys.forEach(knownKeys::remove)

    if (knownKeys.size <= deduplicationPolicy.maxTrackedKeys) {
      if (removableKeys.isNotEmpty()) {
        log.log(
            System.Logger.Level.DEBUG,
            "Spool-backed store pruned committed dedupe keys: removed={0}, remaining={1}, committedSequence={2}",
            removableKeys.size,
            knownKeys.size,
            committedSequence,
        )
      }
      return
    }

    val overflow = knownKeys.size - deduplicationPolicy.maxTrackedKeys
    if (overflow <= 0) {
      return
    }
    val oldestCommittedKeys =
        knownKeys.entries
            .asSequence()
            .filter { it.value <= committedSequence }
            .sortedBy { it.value }
            .take(overflow)
            .map { it.key }
            .toList()
    oldestCommittedKeys.forEach(knownKeys::remove)
    if (oldestCommittedKeys.isNotEmpty()) {
      log.log(
          System.Logger.Level.DEBUG,
          "Spool-backed store trimmed dedupe index by key budget: removed={0}, remaining={1}, committedSequence={2}",
          oldestCommittedKeys.size,
          knownKeys.size,
          committedSequence,
      )
    }
  }
}
