package com.infosung.atomic.storage.image.domain

import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile

internal data class ResolvedImageStorageAccess(
    val storageType: String,
    val storageClient: StorageClient,
    val storageProfile: StorageProfile,
)
