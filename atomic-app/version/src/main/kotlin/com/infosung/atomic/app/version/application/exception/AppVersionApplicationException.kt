package com.infosung.atomic.app.version.application.exception

sealed class AppVersionApplicationException(
    val errorCode: AppVersionErrorCode,
    message: String,
) : RuntimeException(message)
