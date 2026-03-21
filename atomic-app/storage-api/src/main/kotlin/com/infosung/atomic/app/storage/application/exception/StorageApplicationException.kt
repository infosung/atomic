package com.infosung.atomic.app.storage.application.exception

open class StorageApplicationException(
    val errorCode: StorageErrorCode,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
