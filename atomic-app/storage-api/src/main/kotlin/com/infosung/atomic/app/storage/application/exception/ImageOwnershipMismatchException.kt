package com.infosung.atomic.app.storage.application.exception

class ImageOwnershipMismatchException(
    message: String,
    cause: Throwable? = null,
) :
    StorageApplicationException(
        StorageErrorCode.STORAGE_IMAGE_OWNERSHIP_MISMATCH,
        message,
        cause,
    )
