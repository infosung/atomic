package com.infosung.atomic.app.storage.application.exception

class InvalidImageRequestException(
    message: String,
    cause: Throwable? = null,
) : StorageApplicationException(message, cause)
