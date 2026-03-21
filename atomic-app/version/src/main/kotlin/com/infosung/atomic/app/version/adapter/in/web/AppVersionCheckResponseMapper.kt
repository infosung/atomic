package com.infosung.atomic.app.version.adapter.`in`.web

import com.infosung.atomic.app.version.domain.VersionCheckDecision

internal object AppVersionCheckResponseMapper {
  fun toResponse(decision: VersionCheckDecision): AppVersionCheckResponseDto {
    return AppVersionCheckResponseDto(
        currentVersion = decision.currentVersion,
        userVersion = decision.userVersion,
        requiredUpdate = decision.requiredUpdate,
        storeUrl = decision.storeUrl,
    )
  }
}
