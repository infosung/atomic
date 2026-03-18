package com.infosung.atomic.app.version.application.port.`in`

import com.infosung.atomic.app.version.domain.VersionCheckDecision

/** Internal application entry point for app-version evaluation. */
internal fun interface CheckAppVersionUseCase {
  fun check(
      service: String,
      platform: String,
      appVersion: String,
  ): VersionCheckDecision
}
