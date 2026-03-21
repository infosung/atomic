package com.infosung.atomic.app.storage.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

/** Properties for common image API auto-configuration. */
@ConfigurationProperties(prefix = "atomic.app.image")
class AtomicAppImageProperties {
  /** Enables common image upload/delete API. */
  var enabled: Boolean = false

  /** HTTP endpoint path for image API. */
  var endpointPath: String = "/api/v1/storage/image"

  /** Default quality when request has no quality parameter. */
  var defaultQuality: Double = 1.0

  /** Minimum allowed quality. */
  var minQuality: Double = 0.1

  /** Maximum allowed quality. */
  var maxQuality: Double = 1.0

  /** Whether thumbnail generation is enabled by default for uploads. */
  var thumbnailEnabled: Boolean = true

  /**
   * Enables uploader identity parameter handling in image upload/delete API.
   *
   * When enabled:
   * - upload requires uploader parameter and stores it in persisted image metadata
   *   (`image.uploader_id`).
   * - delete requires same uploader parameter and rejects mismatch.
   */
  var uploaderParameterEnabled: Boolean = false

  /**
   * HTTP request parameter name used as uploader identity.
   *
   * Example: `uploaderId`, `memberId`, `ownerKey`.
   */
  var uploaderParameterName: String = "uploaderId"
}
