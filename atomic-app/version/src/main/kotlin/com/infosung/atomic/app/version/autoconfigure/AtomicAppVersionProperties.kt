package com.infosung.atomic.app.version.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/** Properties for common app version API auto-configuration. */
@ConfigurationProperties(prefix = "atomic.app.version")
class AtomicAppVersionProperties {
  /** Enables common version check API. */
  var enabled: Boolean = false

  /** Default store URL when required update target has no explicit URL. */
  var defaultStoreUrl: String = "https://www.infosung.com"

  /** HTTP endpoint path for version check API. */
  var endpointPath: String = "/api/v1/version/check"
}
