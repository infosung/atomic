package com.infosung.atomic.event.log.ingest.api.application.model

import java.io.Serializable
import java.time.Duration

/** Capacity and backpressure policy for the async in-memory intake queue. */
data class EventLogAsyncIngestQueuePolicy(
    val maxBufferedRequestsPerLane: Int = 1_024,
    val maxBufferedBytesPerLane: Long = 16L * 1024 * 1024,
    val enqueueTimeout: Duration = Duration.ofMillis(10),
) : Serializable {
  init {
    require(maxBufferedRequestsPerLane > 0) {
      "maxBufferedRequestsPerLane must be greater than zero."
    }
    require(maxBufferedBytesPerLane > 0) { "maxBufferedBytesPerLane must be greater than zero." }
    require(!enqueueTimeout.isNegative) { "enqueueTimeout must not be negative." }
  }
}
