package com.infosung.atomic.app.storage.application.exception

class InvalidImageRequestException(
    message: String,
    cause: Throwable? = null,
) : StorageApplicationException(StorageErrorCode.STORAGE_INVALID_IMAGE_REQUEST, message, cause)
