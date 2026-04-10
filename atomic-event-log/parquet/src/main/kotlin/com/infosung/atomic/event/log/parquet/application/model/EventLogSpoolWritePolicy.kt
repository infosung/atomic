package com.infosung.atomic.event.log.parquet.application.model

import java.io.Serializable

/** Durability mode for file-backed spool writes. */
enum class EventLogSpoolSyncMode {
  SYNC_ON_APPEND,
  SYNC_ON_CHECKPOINT,
}

/** File spool write policy trading append latency against crash recovery strength. */
data class EventLogSpoolWritePolicy(
    val syncMode: EventLogSpoolSyncMode = EventLogSpoolSyncMode.SYNC_ON_APPEND,
) : Serializable
