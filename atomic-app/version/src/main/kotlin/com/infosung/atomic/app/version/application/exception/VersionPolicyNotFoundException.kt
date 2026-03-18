package com.infosung.atomic.app.version.application.exception

internal class VersionPolicyNotFoundException(
    service: String,
    platform: String,
) :
    AppVersionApplicationException(
        message = "No service version policy found for service=$service, platform=$platform",
    )
