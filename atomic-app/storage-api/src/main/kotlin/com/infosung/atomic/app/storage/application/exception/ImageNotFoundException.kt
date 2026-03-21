package com.infosung.atomic.app.storage.application.exception

class ImageNotFoundException(
    message: String,
    cause: Throwable? = null,
) : StorageApplicationException(StorageErrorCode.STORAGE_IMAGE_NOT_FOUND, message, cause)
