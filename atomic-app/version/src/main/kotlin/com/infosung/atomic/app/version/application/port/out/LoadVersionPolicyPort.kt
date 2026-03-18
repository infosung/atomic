package com.infosung.atomic.app.version.application.port.out

import com.infosung.atomic.app.version.domain.SemanticVersion
import com.infosung.atomic.app.version.domain.VersionPolicy

internal interface LoadVersionPolicyPort {
  fun loadLatestRegistered(service: String, platform: String): VersionPolicy?

  fun loadLatestStoreAvailable(service: String, platform: String): VersionPolicy?

  fun loadRequiredUpdateTargetAbove(
      service: String,
      platform: String,
      version: SemanticVersion,
  ): VersionPolicy?
}
