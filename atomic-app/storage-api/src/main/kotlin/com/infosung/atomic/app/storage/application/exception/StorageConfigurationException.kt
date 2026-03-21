package com.infosung.atomic.app.storage.application.exception

class StorageConfigurationException(
    message: String,
    cause: Throwable? = null,
) : StorageApplicationException(StorageErrorCode.STORAGE_CONFIGURATION_INVALID, message, cause)
