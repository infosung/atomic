package com.infosung.atomic.starter.autoconfigure.idempotency

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties for HTTP idempotency filter auto-configuration. */
@ConfigurationProperties(prefix = "atomic.idempotency")
class AtomicIdempotencyProperties {
  /** Enables idempotency filter auto-configuration. */
  var enabled: Boolean = false

  /** Header name used to read idempotency key. */
  var headerName: String = "Idempotency-Key"

  /** Idempotency record TTL in seconds. */
  var ttlSeconds: Long = 300

  /**
   * Processing lock TTL in seconds while first request is still running.
   *
   * This should usually be longer than the expected max controller processing time.
   */
  var processingTtlSeconds: Long = 3_600

  /** Whether idempotency header is required for configured methods. */
  var requireHeader: Boolean = true

  /** Methods where idempotency enforcement is applied. */
  var includeMethods: MutableList<String> = mutableListOf("POST")

  /** Passes request when store backend fails. */
  var failOpen: Boolean = true

  /** Response header key indicating replayed response. */
  var replayHeaderName: String = "X-Idempotent-Replay"

  /** Response header key indicating replay body is omitted due to size limit. */
  var replayBodyOmittedHeaderName: String = "X-Idempotent-Replay-Body-Omitted"

  /** Max response body bytes cached for replay. */
  var maxCachedBodyBytes: Int = 262_144

  /** In-memory store settings. */
  var inMemory: InMemory = InMemory()

  /** Servlet filter registration settings. */
  var filter: Filter = Filter()

  class InMemory {
    /** Cleanup interval in operation count for expired entries. */
    var cleanupInterval: Int = 1_000
  }

  class Filter {
    /** Enables idempotency filter registration. */
    var enabled: Boolean = true

    /** Servlet filter order. */
    var order: Int = -50

    /** URL patterns applied to idempotency filter. */
    var urlPatterns: MutableList<String> = mutableListOf("/*")
  }

  fun validate() {
    require(headerName.isNotBlank()) { "atomic.idempotency.header-name must not be blank." }
    require(ttlSeconds > 0) { "atomic.idempotency.ttl-seconds must be greater than zero." }
    require(processingTtlSeconds > 0) {
      "atomic.idempotency.processing-ttl-seconds must be greater than zero."
    }
    require(includeMethods.any { it.isNotBlank() }) {
      "atomic.idempotency.include-methods must contain at least one method."
    }
    require(replayHeaderName.isNotBlank()) {
      "atomic.idempotency.replay-header-name must not be blank."
    }
    require(replayBodyOmittedHeaderName.isNotBlank()) {
      "atomic.idempotency.replay-body-omitted-header-name must not be blank."
    }
    require(maxCachedBodyBytes >= 0) {
      "atomic.idempotency.max-cached-body-bytes must be zero or greater."
    }
    require(inMemory.cleanupInterval >= 0) {
      "atomic.idempotency.in-memory.cleanup-interval must be zero or greater."
    }
    if (filter.enabled) {
      require(filter.urlPatterns.isNotEmpty() && filter.urlPatterns.all { it.isNotBlank() }) {
        "atomic.idempotency.filter.url-patterns must be non-blank values when filter is enabled."
      }
    }
  }
}
