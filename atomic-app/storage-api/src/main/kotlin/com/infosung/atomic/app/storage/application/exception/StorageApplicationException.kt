package com.infosung.atomic.app.storage.application.exception

open class StorageApplicationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
