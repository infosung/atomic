package com.infosung.atomic.app.version.application.port.`in`

import com.infosung.atomic.app.version.domain.VersionCheckDecision

/** Application entry point for app-version evaluation. */
fun interface CheckAppVersionUseCase {
  fun check(
      service: String,
      platform: String,
      appVersion: String,
  ): VersionCheckDecision
}
