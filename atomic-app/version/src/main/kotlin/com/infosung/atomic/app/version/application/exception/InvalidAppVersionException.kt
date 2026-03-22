package com.infosung.atomic.app.version.application.exception

class InvalidAppVersionException(
    message: String,
    errorCode: AppVersionErrorCode = AppVersionErrorCode.VERSION_INVALID_APP_VERSION,
) : AppVersionApplicationException(errorCode, message)
