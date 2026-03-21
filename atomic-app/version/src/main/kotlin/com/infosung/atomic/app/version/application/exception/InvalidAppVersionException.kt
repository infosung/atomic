package com.infosung.atomic.app.version.application.exception

class InvalidAppVersionException(
    message: String,
) : AppVersionApplicationException(AppVersionErrorCode.VERSION_INVALID_APP_VERSION, message)
