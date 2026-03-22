package com.infosung.atomic.app.storage.application.exception

class InvalidImageRequestException(
    message: String,
    cause: Throwable? = null,
    errorCode: StorageErrorCode = StorageErrorCode.STORAGE_INVALID_IMAGE_REQUEST,
) : StorageApplicationException(errorCode, message, cause)
