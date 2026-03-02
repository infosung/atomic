package com.infosung.atomic.starter.autoconfigure.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration properties for atomic storage auto-configuration. */
@ConfigurationProperties(prefix = "atomic.storage")
class AtomicStorageProperties {
  /** Enables storage auto-configuration. */
  var enabled: Boolean = true

  /** Storage backends keyed by storage type (for example `S3`, `R2`, `MINIO`). */
  var backends: MutableMap<String, Backend> = linkedMapOf()

  /** One storage backend definition. */
  class Backend {
    /** Enables this backend entry. */
    var enabled: Boolean = true

    /**
     * Backend type for S3-compatible storage.
     *
     * Supported values: `s3`, `r2`, `minio` (all use the same S3-compatible client path).
     */
    var type: String = "s3"

    /** Physical bucket/container name. */
    var bucket: String = ""

    /** Public CDN base URL used for returned URLs. */
    var cdn: String = ""

    /** When true, returned fileName values include `bucket/` prefix. */
    var prependBucketOnObjectKey: Boolean = false

    /** Region value for S3-compatible SDK client. */
    var region: String = ""

    /** Optional custom endpoint. */
    var endpoint: String? = null

    /** Enables path-style access for providers requiring it. */
    var pathStyleAccessEnabled: Boolean = false

    /** Optional static credentials access key. */
    var accessKeyId: String? = null

    /** Optional static credentials secret key. */
    var secretAccessKey: String? = null

    /** Optional session token. */
    var sessionToken: String? = null
  }
}
