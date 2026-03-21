package com.infosung.atomic.app.version.application.exception

class VersionPolicyNotFoundException(
    service: String,
    platform: String,
) :
    AppVersionApplicationException(
        errorCode = AppVersionErrorCode.VERSION_POLICY_NOT_FOUND,
        message = "No service version policy found for service=$service, platform=$platform",
    )
