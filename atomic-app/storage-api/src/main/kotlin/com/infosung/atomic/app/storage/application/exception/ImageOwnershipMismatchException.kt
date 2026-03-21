package com.infosung.atomic.app.storage.application.exception

class ImageOwnershipMismatchException(
    message: String,
    cause: Throwable? = null,
) : StorageApplicationException(message, cause)
