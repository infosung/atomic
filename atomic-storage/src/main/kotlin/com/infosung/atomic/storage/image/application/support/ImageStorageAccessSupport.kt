package com.infosung.atomic.storage.image.application.support

import com.infosung.atomic.storage.StorageClient
import com.infosung.atomic.storage.StorageProfile
import com.infosung.atomic.storage.image.domain.ResolvedImageStorageAccess

internal class ImageStorageAccessSupport(
    private val storageClients: Map<String, StorageClient>,
    private val storageProfiles: Map<String, StorageProfile>,
) {
  fun resolve(storageType: String): ResolvedImageStorageAccess {
    val storageClient =
        storageClients[storageType]
            ?: throw IllegalArgumentException("Unknown storageType: $storageType")
    val storageProfile =
        storageProfiles[storageType]
            ?: throw IllegalArgumentException("Unknown storageType profile: $storageType")
    return ResolvedImageStorageAccess(
        storageType = storageType,
        storageClient = storageClient,
        storageProfile = storageProfile,
    )
  }

  fun toStoredObjectKey(
      access: ResolvedImageStorageAccess,
      objectKey: String,
  ): String {
    if (!access.storageProfile.prependBucketOnObjectKey) return objectKey
    return "${access.storageProfile.bucket}/$objectKey"
  }

  fun toPublicUrl(
      access: ResolvedImageStorageAccess,
      objectKey: String,
  ): String {
    val storedObjectKey = toStoredObjectKey(access, objectKey)
    return "${access.storageProfile.cdn.trimEnd('/')}/$storedObjectKey"
  }

  fun normalizeDeleteObjectKey(
      access: ResolvedImageStorageAccess,
      objectKey: String,
  ): String {
    if (!access.storageProfile.prependBucketOnObjectKey) return objectKey
    val prefix = "${access.storageProfile.bucket}/"
    return if (objectKey.startsWith(prefix)) objectKey.removePrefix(prefix) else objectKey
  }
}
