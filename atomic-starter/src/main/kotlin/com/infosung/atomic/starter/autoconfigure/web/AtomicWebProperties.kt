package com.infosung.atomic.starter.autoconfigure.web

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties for atomic spring-web auto-configuration. */
@ConfigurationProperties(prefix = "atomic.web")
class AtomicWebProperties {
  /** Enables atomic web auto-configuration. */
  var enabled: Boolean = true

  /** Logging feature properties. */
  var logging: Logging = Logging()

  /** JSON masking feature properties. */
  var json: Json = Json()

  /** Request rate-limit feature properties. */
  var rateLimit: RateLimit = RateLimit()

  class Logging {
    /** Enables API logging infrastructure beans. */
    var enabled: Boolean = true

    /** Max in-memory queue size for [com.infosung.atomic.spring.web.log.ServiceLogger]. */
    var queueSize: Int = 10_000

    /** Servlet filter registration settings. */
    var filter: Filter = Filter()
  }

  class Filter {
    /** Enables filter registration bean. */
    var enabled: Boolean = true

    /** Servlet filter order. */
    var order: Int = 1

    /** URL patterns applied to API log filter. */
    var urlPatterns: MutableList<String> = mutableListOf("/*")
  }

  class Json {
    /** Optional regex pattern for sensitive key masking. */
    var sensitiveKeyPattern: String? = null
  }

  class RateLimit {
    /** Enables rate-limit filter registration. */
    var enabled: Boolean = false

    /** Backend store type selection. */
    var store: StoreType = StoreType.AUTO

    /** Default request limit per window. */
    var limit: Long = 100

    /** Default window size in seconds. */
    var windowSeconds: Long = 60

    /** HTTP methods to apply rate-limit on. */
    var includeMethods: MutableList<String> = mutableListOf("GET", "POST", "PUT", "PATCH", "DELETE")

    /** Path prefixes that bypass rate-limit. */
    var excludePathPrefixes: MutableList<String> = mutableListOf("/actuator")

    /** Resolver strategy used for actor key extraction. */
    var keyStrategy: KeyStrategy = KeyStrategy.IP

    /** Header name when [keyStrategy] is [KeyStrategy.HEADER]. */
    var keyHeaderName: String = "X-User-Id"

    /** Behavior when key resolver cannot extract actor key. */
    var missingKeyPolicy: MissingKeyPolicy = MissingKeyPolicy.REJECT

    /** Path-key strategy used in storage key composition. */
    var pathKeyStrategy: PathKeyStrategy = PathKeyStrategy.RULE_PREFIX

    /** Passes request when store backend fails. */
    var failOpen: Boolean = true

    /** Plain-text body written for `429 Too Many Requests`. */
    var responseBody: String = "Too many requests."

    /** Optional path/method-specific rules. First match wins. */
    var rules: MutableList<Rule> = mutableListOf()

    /** Servlet filter registration settings. */
    var filter: RateLimitFilter = RateLimitFilter()

    /** Redis store settings. */
    var redis: Redis = Redis()

    /** In-memory store settings. */
    var inMemory: InMemory = InMemory()

    /** IP resolver settings for [keyStrategy]=[KeyStrategy.IP]. */
    var ip: Ip = Ip()

    fun validate() {
      require(limit > 0) { "atomic.web.rate-limit.limit must be greater than zero." }
      require(windowSeconds > 0) {
        "atomic.web.rate-limit.window-seconds must be greater than zero."
      }
      require(includeMethods.any { it.isNotBlank() }) {
        "atomic.web.rate-limit.include-methods must contain at least one method."
      }
      if (keyStrategy == KeyStrategy.HEADER) {
        require(keyHeaderName.isNotBlank()) {
          "atomic.web.rate-limit.key-header-name must not be blank when key-strategy=HEADER."
        }
      }
      if (store == StoreType.REDIS || store == StoreType.AUTO) {
        require(redis.keyPrefix.isNotBlank()) {
          "atomic.web.rate-limit.redis.key-prefix must not be blank when store=REDIS or AUTO."
        }
      }
      require(inMemory.cleanupInterval >= 0) {
        "atomic.web.rate-limit.in-memory.cleanup-interval must be zero or greater."
      }
      if (filter.urlPatterns.isNotEmpty()) {
        require(filter.urlPatterns.all { it.isNotBlank() }) {
          "atomic.web.rate-limit.filter.url-patterns must contain non-blank patterns only."
        }
      }
      rules.forEachIndexed { index, rule ->
        rule.limit?.let {
          require(it > 0) { "atomic.web.rate-limit.rules[$index].limit must be greater than zero." }
        }
        rule.windowSeconds?.let {
          require(it > 0) {
            "atomic.web.rate-limit.rules[$index].window-seconds must be greater than zero."
          }
        }
      }
    }
  }

  class Rule {
    /** Path prefix matcher. Empty means any path. */
    var pathPrefix: String? = null

    /** Methods matcher. Empty means any method. */
    var methods: MutableList<String> = mutableListOf()

    /** Override limit for this rule. */
    var limit: Long? = null

    /** Override window seconds for this rule. */
    var windowSeconds: Long? = null
  }

  class Redis {
    /** Redis key prefix used by rate-limit store. */
    var keyPrefix: String = "atomic:ratelimit:"
  }

  class InMemory {
    /** Cleanup interval in operation count for expired windows. */
    var cleanupInterval: Int = 1_000
  }

  class Ip {
    /**
     * Trusts forwarding headers (for example `X-Forwarded-For`) for IP extraction.
     *
     * Enable only when your ingress/proxy strips user-supplied forwarding headers.
     */
    var trustForwardedHeaders: Boolean = false
  }

  class RateLimitFilter {
    /** Servlet filter order. */
    var order: Int = -100

    /** URL patterns applied to rate-limit filter. */
    var urlPatterns: MutableList<String> = mutableListOf("/*")
  }

  enum class StoreType {
    AUTO,
    IN_MEMORY,
    REDIS,
    CUSTOM,
  }

  enum class KeyStrategy {
    IP,
    HEADER,
  }

  enum class MissingKeyPolicy {
    REJECT,
    SKIP,
  }

  enum class PathKeyStrategy {
    RULE_PREFIX,
    REQUEST_URI,
  }
}
