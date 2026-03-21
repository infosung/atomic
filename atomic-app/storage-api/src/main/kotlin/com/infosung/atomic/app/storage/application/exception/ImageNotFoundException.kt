package com.infosung.atomic.app.storage.application.exception

class ImageNotFoundException(
    message: String,
    cause: Throwable? = null,
) : StorageApplicationException(message, cause)
