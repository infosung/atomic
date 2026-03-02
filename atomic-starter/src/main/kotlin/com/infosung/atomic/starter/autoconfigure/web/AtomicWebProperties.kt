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
}
