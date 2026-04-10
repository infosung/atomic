package com.infosung.atomic.event.log.parquet.application.model

import java.io.Serializable

/** Bounds the in-memory dedupe index used on the ingest hot path. */
data class EventLogDeduplicationPolicy(
    val retainedCommittedSequenceLag: Long = 100_000,
    val maxTrackedKeys: Int = 200_000,
) : Serializable {
  init {
    require(retainedCommittedSequenceLag >= 0) {
      "retainedCommittedSequenceLag must be zero or greater."
    }
    require(maxTrackedKeys > 0) { "maxTrackedKeys must be greater than zero." }
  }
}
