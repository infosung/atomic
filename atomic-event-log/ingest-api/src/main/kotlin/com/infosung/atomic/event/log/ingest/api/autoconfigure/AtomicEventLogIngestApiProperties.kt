package com.infosung.atomic.event.log.ingest.api.autoconfigure

import com.infosung.atomic.event.log.ingest.api.application.model.EventLogIngestApiMode
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "atomic.event.log.ingest")
class AtomicEventLogIngestApiProperties {
  var enabled: Boolean = false
  var endpointPath: String = "/api/v1/event-logs:batch"
  var collectorIdHeaderName: String? = null
  var mode: EventLogIngestApiMode = EventLogIngestApiMode.ASYNC
  var async: Async = Async()

  class Async {
    var laneCount: Int = 4
    var maxBufferedRequestsPerLane: Int = 1_024
    var maxBufferedBytesPerLane: Long = 16L * 1024 * 1024
    var enqueueTimeout: Duration = Duration.ofMillis(10)
    var workerPollDelay: Duration = Duration.ofMillis(100)
    var workerPollLimit: Int = 256
    var shutdownDrainTimeout: Duration = Duration.ofSeconds(30)
  }
}
