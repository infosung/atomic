package com.infosung.atomic.event.log.adapter.out.store

import com.infosung.atomic.event.log.application.model.EventLogRecord
import com.infosung.atomic.event.log.application.port.out.EventLogStore
import com.infosung.atomic.event.log.application.port.out.EventLogStoreAppendResult

/** In-memory append store for tests and local composition. */
class InMemoryEventLogStore : EventLogStore {
  private val log = System.getLogger(InMemoryEventLogStore::class.java.name)
  private val records = mutableListOf<EventLogRecord>()
  private val dedupeKeys = mutableSetOf<String>()

  override fun append(records: List<EventLogRecord>): List<EventLogStoreAppendResult> =
      synchronized(this) {
        val results = mutableListOf<EventLogStoreAppendResult>()
        records.forEach { record ->
          val dedupeKey = dedupeKey(record)
          if (dedupeKeys.add(dedupeKey)) {
            this.records += record
            results += EventLogStoreAppendResult.ACCEPTED
          } else {
            results += EventLogStoreAppendResult.DUPLICATE
          }
        }
        log.log(
            System.Logger.Level.DEBUG,
            "In-memory event log append finished: count={0}, accepted={1}, duplicate={2}",
            records.size,
            results.count { it == EventLogStoreAppendResult.ACCEPTED },
            results.count { it == EventLogStoreAppendResult.DUPLICATE },
        )
        results
      }

  fun snapshot(): List<EventLogRecord> = synchronized(this) { records.toList() }

  private fun dedupeKey(record: EventLogRecord): String = "${record.serviceId}:${record.eventId}"
}
