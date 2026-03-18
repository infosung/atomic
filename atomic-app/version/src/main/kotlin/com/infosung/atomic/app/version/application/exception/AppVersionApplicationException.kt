package com.infosung.atomic.app.version.application.exception

internal sealed class AppVersionApplicationException(
    message: String,
) : RuntimeException(message)
